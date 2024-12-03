package at.lucny.p2pbackup.core.support;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.HmacUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

public class HashUtils {

    public String generateMac(SecretKey key, List<byte[]> bytes) {
        Mac mac = HmacUtils.getInitializedMac(CryptoConstants.HMAC_BLOCK_ALGORITHM, key.getEncoded());

        for (byte[] buffer : bytes) {
            mac.update(buffer);
        }
        byte[] result = mac.doFinal();

        return Base64.getEncoder().encodeToString(result);
    }

    public String generateMacForFile(Path file, String secret) {
        Mac mac = HmacUtils.getInitializedMac(CryptoConstants.HMAC_BLOCK_ALGORITHM, Base64.getDecoder().decode(secret));
        try (InputStream fis = Files.newInputStream(file, StandardOpenOption.READ)) {
            HmacUtils.updateHmac(mac, fis);
            byte[] result = mac.doFinal();
            return Base64.getEncoder().encodeToString(result);
        } catch (IOException e) {
            throw new IllegalStateException("could not generate mac", e);
        }
    }

    public String generateHashForFile(Path file) {
        MessageDigest digest = DigestUtils.getDigest(CryptoConstants.FILE_HASH_ALGORITHM);
        try {
            byte[] hash = DigestUtils.digest(digest, file, StandardOpenOption.READ);
            return Base64.getEncoder().encodeToString(hash);
        } catch (IOException e) {
            throw new IllegalStateException("could not generate hash", e);
        }
    }

    public String generateBlockHash(byte[] data) {
        MessageDigest digest = DigestUtils.getDigest(CryptoConstants.BLOCK_HASH_ALGORITHM);
        byte[] hash = digest.digest(data);
        return Base64.getEncoder().encodeToString(hash);
    }

    public String generateBlockHash(ByteBuffer data) {
        MessageDigest digest = DigestUtils.getDigest(CryptoConstants.BLOCK_HASH_ALGORITHM);
        byte[] hash = DigestUtils.digest(digest, data);
        return Base64.getEncoder().encodeToString(hash);
    }
}
