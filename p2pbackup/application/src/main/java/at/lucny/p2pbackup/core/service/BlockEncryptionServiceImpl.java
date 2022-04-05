package at.lucny.p2pbackup.core.service;

import at.lucny.p2pbackup.core.support.CryptoUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@Validated
public class BlockEncryptionServiceImpl implements BlockEncryptionService {

    private static final int IV_LENGTH = 96;

    private static final int GCM_TAG_LENGTH = 128;

    private static final String SALT_BLOCK_ENCRYPTION = "blockEncryption";

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";

    private final CryptoUtils cryptoUtils;

    private final SecretKey secretKey;


    public BlockEncryptionServiceImpl(CryptoUtils cryptoUtils, CryptoService cryptoService) {
        this.cryptoUtils = cryptoUtils;
        this.secretKey = cryptoService.getSecretKeyGenerator().generateAES(SALT_BLOCK_ENCRYPTION);
    }

    private void checkBufferSize(ByteBuffer readBuffer, ByteBuffer writeBuffer) {
        if (readBuffer.remaining() * 5 > writeBuffer.remaining()) {
            throw new IllegalArgumentException("target buffer could be to small. bytes to read: " + readBuffer.remaining() + ". remaining bytes in write buffer: " + writeBuffer.remaining());
        }
    }

    @Override
    public void encrypt(ByteBuffer plainData, byte[] aead, ByteBuffer encryptedData) {
        this.checkBufferSize(plainData, encryptedData);

        try {
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            this.cryptoUtils.getSecureRandom().nextBytes(iv);

            GCMParameterSpec gcmParameter = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, gcmParameter);
            if (aead != null) {
                cipher.updateAAD(aead);
            }

            encryptedData.put(iv);
            cipher.doFinal(plainData, encryptedData);
            encryptedData.flip();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void decrypt(ByteBuffer encryptedData, byte[] aead, ByteBuffer plainData) {
        this.checkBufferSize(encryptedData, plainData);

        try {
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            encryptedData.get(iv);
            GCMParameterSpec gcmParameter = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, this.secretKey, gcmParameter);
            if (aead != null) {
                cipher.updateAAD(aead);
            }

            cipher.doFinal(encryptedData, plainData);
            plainData.flip();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | ShortBufferException e) {
            throw new IllegalStateException(e);
        }
    }
}
