package at.lucny.p2pbackup.core.support;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.crypto.spec.PBEParameterSpec;
import javax.net.ssl.KeyManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Objects;

@Service
@Validated
public class CryptoUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoUtils.class);

    private static final Provider BC_PROVIDER = new BouncyCastleProvider();

    private static final String KEYSTORE_TYPE = "PKCS12";

    private final SecureRandom secureRandom;

    public CryptoUtils() throws NoSuchAlgorithmException, NoSuchProviderException {
        // initialize the SecureRandom. use the native source only as seed
        // see https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html#GUID-9DC4ADD5-6D01-4B2E-9E85-B88E3BEE7453
        String[][] secureRandomProviders = new String[][]{{"Windows-PRNG", "SunMSCAPI"}, {"NativePRNG", "SUN"}};
        SecureRandom createdSecureRandom = null;
        for (String[] secureRandomProvider : secureRandomProviders) {
            try {
                createdSecureRandom = SecureRandom.getInstance(secureRandomProvider[0], secureRandomProvider[1]);
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
                LOGGER.debug("could not create SecureRandom with parameters {}:{}", secureRandomProvider[0], secureRandomProvider[1]);
            }
        }
        if (createdSecureRandom != null) {
            byte[] seed = createdSecureRandom.generateSeed(256);
            createdSecureRandom = SecureRandom.getInstance("DRBG");
            createdSecureRandom.setSeed(seed);
        } else {
            LOGGER.warn("unable to load native random provider");
            createdSecureRandom = SecureRandom.getInstance("DRBG");
        }
        Objects.requireNonNull(createdSecureRandom, "unable to create SecureRandom");
        this.secureRandom = createdSecureRandom;
    }

    public SecureRandom getSecureRandom() {
        return secureRandom;
    }

    public byte[] nextBytes(int amount) {
        byte[] array = new byte[amount];
        this.secureRandom.nextBytes(array);
        return array;
    }

    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(CryptoConstants.KEYPAIR_ALGORITHM);
            generator.initialize(CryptoConstants.KEYPAIR_KEYSIZE, this.secureRandom);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("could not generate keypair with algorithm=" + CryptoConstants.KEYPAIR_ALGORITHM + ", keysize=" + CryptoConstants.KEYPAIR_KEYSIZE, e);
        }
    }

    public X509Certificate createCACertificate(KeyPair keypair, String owner) {
        try {
            var issuer = new X500Name("CN=" + owner);
            var subject = issuer;
            var serial = BigInteger.valueOf(this.secureRandom.nextLong());
            var dueFrom = new Date(OffsetDateTime.now().minusDays(1).toInstant().toEpochMilli());
            var dueTo = new Date(OffsetDateTime.now().plusYears(10).toInstant().toEpochMilli());

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial, dueFrom, dueTo, subject, keypair.getPublic());

            // Add Extensions
            // A BasicConstraint to mark root certificate as CA certificate
            JcaX509ExtensionUtils rootCertExtUtils = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
            builder.addExtension(Extension.subjectKeyIdentifier, false, rootCertExtUtils.createSubjectKeyIdentifier(keypair.getPublic()));

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keypair.getPrivate());
            X509CertificateHolder certHolder = builder.build(signer);
            X509Certificate cert = new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(certHolder);
            cert.verify(keypair.getPublic());
            return cert;
        } catch (OperatorCreationException | CertificateException | NoSuchAlgorithmException | InvalidKeyException | NoSuchProviderException | SignatureException | CertIOException e) {
            throw new IllegalStateException(e);
        }
    }

    public X509Certificate createCertificate(KeyPair issuedKeyPair, KeyPair rootKeyPair, X509Certificate
            rootCertificate, String owner) {
        try {
            var issuer = new JcaX509CertificateHolder(rootCertificate).getSubject();
            var subject = new X500Name("CN=" + owner + "-" + System.currentTimeMillis());
            var serial = BigInteger.valueOf(this.secureRandom.nextLong());
            var dueFrom = new Date(OffsetDateTime.now().minusDays(1).toInstant().toEpochMilli());
            var dueTo = new Date(OffsetDateTime.now().plusDays(7).toInstant().toEpochMilli());

            PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(subject, issuedKeyPair.getPublic());

            // Sign the new KeyPair with the root cert Private Key
            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(rootKeyPair.getPrivate());
            PKCS10CertificationRequest csr = p10Builder.build(signer);

            // Use the Signed KeyPair and CSR to generate an issued Certificate
            // Here serial number is randomly generated. In general, CAs use
            // a sequence to generate Serial number and avoid collisions
            X509v3CertificateBuilder issuedCertBuilder = new X509v3CertificateBuilder(issuer, serial, dueFrom, dueTo, csr.getSubject(), csr.getSubjectPublicKeyInfo());

            // Add Extensions
            JcaX509ExtensionUtils issuedCertExtUtils = new JcaX509ExtensionUtils();
            // Use BasicConstraints to say that this Cert is not a CA
            issuedCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

            // Add Issuer cert identifier as Extension
            issuedCertBuilder.addExtension(Extension.authorityKeyIdentifier, false, issuedCertExtUtils.createAuthorityKeyIdentifier(rootCertificate));
            issuedCertBuilder.addExtension(Extension.subjectKeyIdentifier, false, issuedCertExtUtils.createSubjectKeyIdentifier(csr.getSubjectPublicKeyInfo()));

            X509CertificateHolder issuedCertHolder = issuedCertBuilder.build(signer);
            X509Certificate issuedCert = new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(issuedCertHolder);

            // Verify the issued cert signature against the root (issuer) cert
            issuedCert.verify(rootCertificate.getPublicKey(), BC_PROVIDER);
            return issuedCert;
        } catch (OperatorCreationException | CertificateException | NoSuchAlgorithmException | InvalidKeyException | SignatureException | CertIOException e) {
            throw new IllegalStateException(e);
        }
    }

    public KeyStore createTemporaryKeyStore(KeyPair keyPair, X509Certificate[] certificateChain, String alias,
                                            char[] keyPassword) throws IOException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(null, null);
            keyStore.setKeyEntry(alias, keyPair.getPrivate(), keyPassword, certificateChain);
            return keyStore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new IOException("Could not create keystore and add keypair", e);
        }
    }

    public KeyStore createKeyStore(KeyPair keyPair, X509Certificate[] certificateChain, String alias,
                                   char[] keystorePassword, char[] keyPassword) throws IOException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(null, keystorePassword);
            KeyStore.PrivateKeyEntry privateKeyEntry = new KeyStore.PrivateKeyEntry(keyPair.getPrivate(), certificateChain);
            byte[] salt = new byte[16];
            this.secureRandom.nextBytes(salt);
            keyStore.setEntry(alias, privateKeyEntry,
                    new KeyStore.PasswordProtection(keyPassword, "PBEWithHmacSHA512AndAES_256",
                            new PBEParameterSpec(salt, 65536)));
            return keyStore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new IOException("Could not create keystore and add keypair", e);
        }
    }

    public KeyStore writeKeyStore(KeyPair keyPair, X509Certificate certificate, String alias, Path fileName,
                                  char[] password) throws IOException {
        try {
            KeyStore keyStore = this.createKeyStore(keyPair, new X509Certificate[]{certificate}, alias, password, password);
            try (OutputStream keyStoreOs = Files.newOutputStream(fileName, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
                keyStore.store(keyStoreOs, password);
            }
            return keyStore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new IOException("Could not write KeyPair to keystore", e);
        }
    }

    public KeyStore readKeyStore(Path fileName, char[] password) throws IOException {
        try {
            KeyStore keystore = KeyStore.getInstance(KEYSTORE_TYPE);
            try (InputStream is = Files.newInputStream(fileName, StandardOpenOption.READ)) {
                keystore.load(is, password);
            }
            return keystore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new IOException("Could not load keystore " + fileName, e);
        }
    }

    public KeyManagerFactory createKeyManagerFactory(KeyStore keyStore, char[] password) throws KeyStoreException {
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keyStore, password);
            return kmf;
        } catch (NoSuchAlgorithmException | KeyStoreException | UnrecoverableKeyException e) {
            throw new KeyStoreException("could not create KeyManagerFactory from keystore", e);
        }

    }

}
