package at.lucny.p2pbackup.core.service;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;

public interface BlockEncryptionService {

    /**
     * Encrypts the remaining data of the plainData-ByteBuffer onto the encryptedData-ByteBuffer.
     * Flips the encryptedData-ByteBuffer afterwards.
     *
     * @param plainData
     * @param aead
     * @param encryptedData
     */
    void encrypt(@NotNull ByteBuffer plainData, byte[] aead, @NotNull ByteBuffer encryptedData);

    /**
     * Decrypts the remaining data of the encryptedData-ByteBuffer onto the plainData-ByteBuffer.
     * Flips the plainData-ByteBuffer afterwards.
     *
     * @param encryptedData
     * @param aead
     * @param plainData
     */
    void decrypt(@NotNull ByteBuffer encryptedData, byte[] aead, @NotNull ByteBuffer plainData);
}
