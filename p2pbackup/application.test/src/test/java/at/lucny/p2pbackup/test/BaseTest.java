package at.lucny.p2pbackup.test;

import at.lucny.p2pbackup.backup.support.BackupUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseTest {

    @BeforeAll
    static void beforeAll() {
        Security.addProvider(new BouncyCastleProvider()); // register bouncycastle-provider
    }

    public static Path createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<byte[]> generateBytesInBlocks(int nrOfBlocks) throws IOException {
        List<byte[]> content = new ArrayList<>();
        for (int i = 0; i < nrOfBlocks; i++) {
            byte[] bytes = new byte[100 * 1024];
            BackupUtils.RANDOM.nextBytes(bytes);
            content.add(bytes);
        }
        return content;
    }

    public static byte[] generateBytes(int nrOfBlocks) throws IOException {
        List<byte[]> content = generateBytesInBlocks(nrOfBlocks);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (byte[] bytes : content) {
            baos.write(bytes);
        }
        return baos.toByteArray();
    }
}
