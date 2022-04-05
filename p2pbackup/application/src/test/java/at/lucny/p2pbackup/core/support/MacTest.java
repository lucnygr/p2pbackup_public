package at.lucny.p2pbackup.core.support;

import org.apache.commons.codec.digest.HmacUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MacTest {

    private SecureRandom random = new SecureRandom();

    @Test
    void testId() throws IOException {
        byte[] key = new byte[16];
        random.nextBytes(key);
        Mac mac = HmacUtils.getInitializedMac(CryptoConstants.HMAC_BLOCK_ALGORITHM, key);

        byte[] outData = new byte[1024 * 100 + 16];
        random.nextBytes(outData);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MacOutputStream mos = new MacOutputStream(bos, mac);
        mos.write(outData);
        byte[] macFromOutput = mac.doFinal();

        mac.reset();
        MacInputStream mis = new MacInputStream(new ByteArrayInputStream(bos.toByteArray()), mac);
        byte[] inData = new byte[1024 * 100 + 16];
        mis.read(inData);
        byte[] macFromInput = mac.doFinal();

        assertThat(inData).containsExactly(outData);
        assertThat(macFromInput).containsExactly(macFromOutput);

    }

    @Test
    void testSingleByteReads() throws IOException {
        byte[] key = new byte[16];
        random.nextBytes(key);
        Mac mac = HmacUtils.getInitializedMac(CryptoConstants.HMAC_BLOCK_ALGORITHM, key);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MacOutputStream mos = new MacOutputStream(bos, mac);

        mos.write(1);
        mos.write(3);
        mos.write(5);
        byte[] macFromOutput = mac.doFinal();

        mac.reset();
        MacInputStream mis = new MacInputStream(new ByteArrayInputStream(bos.toByteArray()), mac);
        assertThat(mis.read()).isEqualTo(1);
        assertThat(mis.read()).isEqualTo(3);
        assertThat(mis.read()).isEqualTo(5);
        byte[] macFromInput = mac.doFinal();

        assertThat(macFromInput).containsExactly(macFromOutput);

        assertThat(mis.read()).isEqualTo(-1);
    }

}
