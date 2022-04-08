package at.lucny.p2pbackup.core.service;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

public interface BlockEncryptionService {

    /**
     * Encrypts the remaining data of the plainData-ByteBuffer onto the encryptedData-ByteBuffer.
     * Flips the encryptedData-ByteBuffer afterwards.
     *
     * @param plainData     the plain data
     * @param aead          additional information to be integrity-checked, can be null
     * @param encryptedData the encrypted data
     */
    void encrypt(@NotNull ByteBuffer plainData, byte[] aead, @NotNull ByteBuffer encryptedData);

    /**
     * Encrypts the remaining data of the plainData-ByteBuffer by using {@link BlockEncryptionService#encrypt(ByteBuffer, byte[], Consumer)}
     * and calls the consumer with the encrypted data.
     *
     * @param plainData the plain data
     * @param aead      additional information to be integrity-checked, can be null
     * @param consumer  the encrypted data
     */
    void encrypt(ByteBuffer plainData, byte[] aead, Consumer<ByteBuffer> consumer);

    /**
     * Decrypts the remaining data of the encryptedData-ByteBuffer onto the plainData-ByteBuffer.
     * Flips the plainData-ByteBuffer afterwards.
     *
     * @param encryptedData the encrypted data
     * @param aead          additional information to be integrity-checked, can be null
     * @param plainData     the plain data
     */
    void decrypt(@NotNull ByteBuffer encryptedData, byte[] aead, @NotNull ByteBuffer plainData);

    /**
     * Decrypts the remaining data of the encryptedData-ByteBuffer by using {@link BlockEncryptionService#decrypt(ByteBuffer, byte[], ByteBuffer)}
     * and calls the consumer with the plain data.
     *
     * @param encryptedData the encrypted data
     * @param aead          additional information to be integrity-checked, can be null
     * @param consumer      the plain data
     */
    void decrypt(ByteBuffer encryptedData, byte[] aead, Consumer<ByteBuffer> consumer);

}
