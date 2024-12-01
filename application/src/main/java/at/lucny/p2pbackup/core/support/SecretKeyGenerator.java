package at.lucny.p2pbackup.core.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Objects;

public class SecretKeyGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretKeyGenerator.class);

    private static final String SALT_KDF_MASTER_KEY = "master_KEY_salt_";

    private final SecretKey masterKey;

    private final int keyLength;

    private final int iterationCount;

    public SecretKeyGenerator(byte[] keyBase) {
        this(keyBase, 256, 65536);
    }

    public SecretKeyGenerator(byte[] keyBase, int keyLength, int iterationCount) {
        this.keyLength = keyLength;
        this.iterationCount = iterationCount;
        this.masterKey = this.generate(keyBase, SALT_KDF_MASTER_KEY, this.keyLength);
    }

    public SecretKey generate(String salt) {
        return this.generate(salt, this.keyLength);
    }

    public SecretKey generateAES(String salt) {
        SecretKey key = this.generate(salt, this.keyLength);
        return new SecretKeySpec(key.getEncoded(), "AES");
    }

    public SecretKey generate(String salt, int keyLength) {
        return this.generate(this.masterKey.getEncoded(), salt, keyLength);
    }

    private SecretKey generate(byte[] secret, String salt, int keyLength) {
        Objects.requireNonNull(secret, "no secret as base for key generation was given");
        Objects.requireNonNull(salt, "no salt given for key generation");
        LOGGER.debug("generating secret key for salt={}, keyLength={}", salt, keyLength);

        String base64EncodedSecret = Base64.getEncoder().encodeToString(secret);
        char[] password = base64EncodedSecret.toCharArray();
        KeySpec spec = new PBEKeySpec(password, salt.getBytes(StandardCharsets.UTF_8), this.iterationCount, keyLength);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(CryptoConstants.KEY_DERIVATION_FUNCTION);
            SecretKey secretKey = factory.generateSecret(spec);
            LOGGER.debug("generated secret key for salt {}", salt);
            return secretKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("unable to generate secret-key for salt " + salt, e);
        }
    }

}
