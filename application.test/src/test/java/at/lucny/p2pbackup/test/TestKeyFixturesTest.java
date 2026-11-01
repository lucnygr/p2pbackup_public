package at.lucny.p2pbackup.test;

import at.lucny.p2pbackup.core.support.CryptoUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyFixturesTest {

    @Test
    void copyTo_providesLoadableKeystoreAndCertificate(@TempDir Path configDir) throws Exception {
        TestKeyFixtures.copyTo("user1", configDir);

        assertThat(configDir.resolve("user1.pem")).isRegularFile();
        KeyStore keyStore = new CryptoUtils().readKeyStore(configDir.resolve("user1.pfx"), TestKeyFixtures.passwordFor("user1").toCharArray());
        assertThat(keyStore.size()).isPositive();
    }

    @Test
    void copyTo_isGeneratedOnlyOnceAndCopiesAreIndependent(@TempDir Path dir1, @TempDir Path dir2) throws Exception {
        TestKeyFixtures.copyTo("user2", dir1);
        TestKeyFixtures.copyTo("user2", dir2);

        assertThat(Files.readAllBytes(dir1.resolve("user2.pfx"))).isEqualTo(Files.readAllBytes(dir2.resolve("user2.pfx")));
        Files.delete(dir1.resolve("user2.pfx"));
        assertThat(dir2.resolve("user2.pfx")).exists();
    }

    @Test
    void differentUsersGetDifferentKeys(@TempDir Path dir) throws Exception {
        TestKeyFixtures.copyTo("user1", dir);
        TestKeyFixtures.copyTo("user3", dir);

        assertThat(Files.readAllBytes(dir.resolve("user1.pem"))).isNotEqualTo(Files.readAllBytes(dir.resolve("user3.pem")));
    }
}
