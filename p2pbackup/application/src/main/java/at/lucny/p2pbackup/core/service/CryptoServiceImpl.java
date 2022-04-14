package at.lucny.p2pbackup.core.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.dto.AuthenticationKeys;
import at.lucny.p2pbackup.core.support.CertificateUtils;
import at.lucny.p2pbackup.core.support.CryptoConstants;
import at.lucny.p2pbackup.core.support.CryptoUtils;
import at.lucny.p2pbackup.core.support.SecretKeyGenerator;
import at.lucny.p2pbackup.user.domain.User;
import at.lucny.p2pbackup.user.repository.UserRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

@Service
@Validated
public class CryptoServiceImpl implements CryptoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoServiceImpl.class);

    private final CertificateUtils certificateUtils = new CertificateUtils();

    private final P2PBackupProperties p2PBackupProperties;

    private final UserRepository userRepository;

    private final CryptoUtils cryptoUtils;

    private final SecretKeyGenerator secretKeyGenerator;

    private final KeyManagerFactory tlsKeyManagerFactory;

    public CryptoServiceImpl(P2PBackupProperties p2PBackupProperties, CryptoUtils cryptoUtils, @Lazy UserRepository userRepository, @Value("${at.lucny.p2p-backup.password:}") String givenPassword) throws IOException, KeyStoreException, CertificateEncodingException {
        LOGGER.info("creating crypto-service");
        this.p2PBackupProperties = p2PBackupProperties;
        this.userRepository = userRepository;
        this.cryptoUtils = cryptoUtils;

        char[] password = this.getPassword(this.p2PBackupProperties.getKeystore().getFile().toPath().getFileName().toString(), givenPassword);

        AuthenticationKeys authenticationKeys;
        if (this.p2PBackupProperties.getKeystore().exists()) {
            authenticationKeys = this.loadAuthenticationKeys(this.p2PBackupProperties.getKeystore().getFile().toPath(), password);
        } else {
            LOGGER.info("keystore {} does not exist, try to generate authentication-keys", this.p2PBackupProperties.getKeystore());
            authenticationKeys = this.generateAuthenticationKeys(password);
        }
        Arrays.fill(password, '0');

        String sha256Fingerprint = DigestUtils.sha256Hex(authenticationKeys.certificate().getEncoded());
        String sha512Fingerprint = DigestUtils.sha3_512Hex(authenticationKeys.certificate().getEncoded());
        LOGGER.info("the root-certificates hashes are\nSHA-256 {}\nSHA3-512 {}", sha256Fingerprint, sha512Fingerprint);

        LOGGER.debug("generating tls key-pair and certificate");
        KeyPair tlsAuthenticationKeyPair = this.cryptoUtils.generateKeyPair();
        X509Certificate tlsCert = this.cryptoUtils.createCertificate(tlsAuthenticationKeyPair, authenticationKeys.keyPair(), authenticationKeys.certificate(), this.p2PBackupProperties.getUser());

        LOGGER.debug("generating password for temporary keystore");
        byte[] secret = new byte[256];
        this.cryptoUtils.getSecureRandom().nextBytes(secret);
        char[] tlsKeyPassword = Base64.getEncoder().encodeToString(secret).toCharArray();

        LOGGER.debug("generating temporary keystore");
        KeyStore tlsKeyStore =
                this.cryptoUtils.createTemporaryKeyStore(tlsAuthenticationKeyPair, new X509Certificate[]{tlsCert, authenticationKeys.certificate()}, this.p2PBackupProperties.getUser(), tlsKeyPassword);
        this.tlsKeyManagerFactory = this.cryptoUtils.createKeyManagerFactory(tlsKeyStore, tlsKeyPassword);

        Arrays.fill(secret, (byte) 0);
        Arrays.fill(tlsKeyPassword, '0');

        AuthenticationKeys encryptionKeys;
        if (this.p2PBackupProperties.getOldKeystore() != null) {
            LOGGER.debug("using old keystore for encryption");
            if (!this.p2PBackupProperties.getOldKeystore().exists()) {
                throw new IllegalStateException("configured old keystore " + this.p2PBackupProperties.getOldKeystore() + ", but it does not exist");
            }
            password = this.getPassword(this.p2PBackupProperties.getOldKeystore().getFile().toPath().getFileName().toString(), givenPassword);
            encryptionKeys = this.loadAuthenticationKeys(this.p2PBackupProperties.getOldKeystore().getFile().toPath(), password);
        } else {
            encryptionKeys = authenticationKeys;
        }

        this.secretKeyGenerator = new SecretKeyGenerator(encryptionKeys.keyPair().getPrivate().getEncoded());
        Arrays.fill(password, '0');
    }

    private char[] getPassword(String path, String givenPassword) {
        char[] password = null;
        if (givenPassword != null) {
            LOGGER.warn("using provided password. this is a security risk and should not be used.");
            password = givenPassword.toCharArray();
        } else {
            Console console = System.console();
            if (console != null) {
                password = console.readPassword("Please input the password for your private key %s:", path);
            } else {
                System.out.printf("Please input the password for your private key %s:", path);
                Scanner scanner = new Scanner(System.in);
                password = scanner.nextLine().toCharArray();
            }
        }
        return password;
    }

    private AuthenticationKeys generateAuthenticationKeys(char[] password) throws IOException {
        LOGGER.trace("begin generateAuthenticationKeys(password=****)");

        LOGGER.debug("generating authentication-keys for alias {}", this.p2PBackupProperties.getUser());
        KeyPair rootKeyPair = this.cryptoUtils.generateKeyPair();
        LOGGER.debug("generating certificate for alias {}", this.p2PBackupProperties.getUser());
        X509Certificate rootPublicKeyCertificate = this.cryptoUtils.createCACertificate(rootKeyPair, this.p2PBackupProperties.getUser());

        LOGGER.debug("writing authentication-keys to {}", this.p2PBackupProperties.getKeystore());
        this.cryptoUtils.writeKeyStore(rootKeyPair, rootPublicKeyCertificate, this.p2PBackupProperties.getUser(), this.p2PBackupProperties.getKeystore().getFile().toPath(), password);

        Path certificatePath = this.p2PBackupProperties.getCertificate().getFile().toPath();
        LOGGER.info("writing certificate to {}", certificatePath);
        this.certificateUtils.writeCertificate(rootPublicKeyCertificate, certificatePath);

        LOGGER.trace("end generateAuthenticationKeys");
        return new AuthenticationKeys(rootKeyPair, rootPublicKeyCertificate);
    }

    private AuthenticationKeys loadAuthenticationKeys(Path keystorePath, char[] password) throws IOException {
        LOGGER.trace("begin loadAuthenticationKeys(password=****)");

        try {
            LOGGER.debug("loading authentication-keys from {} for alias {}", keystorePath, this.p2PBackupProperties.getUser());
            KeyStore keystore = this.cryptoUtils.readKeyStore(keystorePath, password);
            PrivateKey rootPrivateKey = (PrivateKey) keystore.getKey(this.p2PBackupProperties.getUser(), password);
            X509Certificate rootPublicKeyCertificate = (X509Certificate) keystore.getCertificate(this.p2PBackupProperties.getUser());
            KeyPair rootKeyPair = new KeyPair(rootPublicKeyCertificate.getPublicKey(), rootPrivateKey);
            return new AuthenticationKeys(rootKeyPair, rootPublicKeyCertificate);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new IllegalStateException("could not load authentication-keys", e);
        } finally {
            LOGGER.trace("end loadAuthenticationKeys");
        }
    }

    @Override
    public SecretKeyGenerator getSecretKeyGenerator() {
        return secretKeyGenerator;
    }

    @Override
    public SSLEngine createSslEngine(boolean useAsClient) {
        LOGGER.trace("begin createSslEngine(useAsClient={})", useAsClient);

        List<User> users = userRepository.findAll();

        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            for (User user : users) {
                trustStore.setCertificateEntry(user.getId(), this.certificateUtils.readCertificate(user.getCertificate()));
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(trustStore);

            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(this.tlsKeyManagerFactory.getKeyManagers(), tmf.getTrustManagers(), this.cryptoUtils.getSecureRandom());

            SSLEngine engine = sslCtx.createSSLEngine();
            engine.setUseClientMode(useAsClient);
            engine.setNeedClientAuth(true);

            String[] enProtocols = engine.getEnabledProtocols();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Enabled protocols are: {}", Arrays.toString(enProtocols));
            }
            if (!Arrays.asList(enProtocols).contains(CryptoConstants.TLS_VERSION)) {
                throw new IllegalStateException("protocol version " + CryptoConstants.TLS_VERSION + " not supported");
            }
            engine.setEnabledProtocols(new String[]{CryptoConstants.TLS_VERSION});

            String[] enCiphersuite = engine.getEnabledCipherSuites();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Enabled ciphersuites are: {}", Arrays.toString(enCiphersuite));
            }
            engine.setEnabledCipherSuites(CryptoConstants.TLS_CIPHERS);

            return engine;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | KeyManagementException | IOException e) {
            throw new IllegalStateException("unable to create ssl-engine", e);
        } finally {
            LOGGER.trace("end createSslEngine");
        }
    }
}
