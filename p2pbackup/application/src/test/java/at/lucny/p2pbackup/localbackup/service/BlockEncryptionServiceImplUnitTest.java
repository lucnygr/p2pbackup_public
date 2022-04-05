package at.lucny.p2pbackup.localbackup.service;

import at.lucny.p2pbackup.core.service.BlockEncryptionServiceImpl;
import at.lucny.p2pbackup.core.service.CryptoService;
import at.lucny.p2pbackup.core.support.CryptoUtils;
import at.lucny.p2pbackup.core.support.SecretKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.AEADBadTagException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockEncryptionServiceImplUnitTest {

    private BlockEncryptionServiceImpl blockEncryptionService;

    @Mock
    private CryptoService cryptoServiceMock;

    @Mock
    private SecretKeyGenerator secretKeyGeneratorMock;

    @BeforeEach
    void beforeEach() throws NoSuchAlgorithmException, NoSuchProviderException {
        when(this.cryptoServiceMock.getSecretKeyGenerator()).thenReturn(this.secretKeyGeneratorMock);
        SecretKey secretKey = KeyGenerator.getInstance("AES").generateKey();
        when(this.secretKeyGeneratorMock.generateAES("blockEncryption")).thenReturn(secretKey);

        this.blockEncryptionService = new BlockEncryptionServiceImpl(new CryptoUtils(), this.cryptoServiceMock);
    }

    @Test
    void testEncrypt_withByteBufferToSmall() {
        ByteBuffer data = ByteBuffer.wrap("This is my Testdata".getBytes(StandardCharsets.UTF_8));
        ByteBuffer encryptedData = ByteBuffer.allocate(data.remaining() * 5 - 1);

        assertThatThrownBy(() -> this.blockEncryptionService.encrypt(data, null, encryptedData))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecrypt_withByteBufferToSmall() {
        ByteBuffer encryptedData = ByteBuffer.wrap("This is my Testdata".getBytes(StandardCharsets.UTF_8));
        ByteBuffer data = ByteBuffer.allocate(encryptedData.remaining() * 5 - 1);

        assertThatThrownBy(() -> this.blockEncryptionService.decrypt(encryptedData, null, data))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEncryptAndDecrypt() {
        ByteBuffer data = ByteBuffer.wrap("This is my Testdata".getBytes(StandardCharsets.UTF_8));
        ByteBuffer encryptedData = ByteBuffer.allocate(1024);

        this.blockEncryptionService.encrypt(data, null, encryptedData);

        data.rewind();
        assertThat(data.remaining()).isNotEqualTo(encryptedData.remaining());

        ByteBuffer plainData = ByteBuffer.allocate(1024);
        this.blockEncryptionService.decrypt(encryptedData, null, plainData);

        assertThat(data.remaining()).isEqualTo(plainData.remaining());
        for (int i = 0; i < data.remaining(); i++) {
            assertThat(data.get()).isEqualTo(plainData.get());
        }
    }

    @Test
    void testEncryptAndDecrypt_ciphertextModified() {
        ByteBuffer data = ByteBuffer.wrap("This is my Testdata".getBytes(StandardCharsets.UTF_8));
        ByteBuffer encryptedData = ByteBuffer.allocate(1024);

        this.blockEncryptionService.encrypt(data, null, encryptedData);

        data.rewind();
        assertThat(data.remaining()).isNotEqualTo(encryptedData.remaining());

        encryptedData.putInt(32); // overwrite the first bytes of the buffer
        encryptedData.rewind();

        ByteBuffer plainData = ByteBuffer.allocate(1024);
        assertThatThrownBy(() -> this.blockEncryptionService.decrypt(encryptedData, null, plainData))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseExactlyInstanceOf(AEADBadTagException.class);
    }

    @Test
    void testEncryptAndDecrypt_withAAD() {
        ByteBuffer data = ByteBuffer.wrap("This is my Testdata".getBytes(StandardCharsets.UTF_8));
        ByteBuffer encryptedData = ByteBuffer.allocate(1024);

        this.blockEncryptionService.encrypt(data, "MyAAD".getBytes(StandardCharsets.UTF_8), encryptedData);

        data.rewind();
        assertThat(data.remaining()).isNotEqualTo(encryptedData.remaining());

        ByteBuffer plainData = ByteBuffer.allocate(1024);
        this.blockEncryptionService.decrypt(encryptedData, "MyAAD".getBytes(StandardCharsets.UTF_8), plainData);

        assertThat(data.remaining()).isEqualTo(plainData.remaining());
        for (int i = 0; i < data.remaining(); i++) {
            assertThat(data.get()).isEqualTo(plainData.get());
        }
    }

    @Test
    void testEncryptAndDecrypt_withAADWrong() {
        ByteBuffer data = ByteBuffer.wrap("This is my Testdata".getBytes(StandardCharsets.UTF_8));
        ByteBuffer encryptedData = ByteBuffer.allocate(1024);

        this.blockEncryptionService.encrypt(data, "MyAAD".getBytes(StandardCharsets.UTF_8), encryptedData);

        data.rewind();
        assertThat(data.remaining()).isNotEqualTo(encryptedData.remaining());

        byte[] wrongAAD = "MyWrongAAD".getBytes(StandardCharsets.UTF_8);
        ByteBuffer plainData = ByteBuffer.allocate(1024);
        assertThatThrownBy(() -> this.blockEncryptionService.decrypt(encryptedData, wrongAAD, plainData))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseExactlyInstanceOf(AEADBadTagException.class);
    }

}
