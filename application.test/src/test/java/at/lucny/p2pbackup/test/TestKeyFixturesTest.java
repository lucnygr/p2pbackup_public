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
    void generateTo_providesLoadableKeystoreAndCertificate(@TempDir Path configDir) throws Exception {
        TestKeyFixtures.generateTo("user1", configDir);

        assertThat(configDir.resolve("user1.pem")).isRegularFile();
        KeyStore keyStore = new CryptoUtils().readKeyStore(configDir.resolve("user1.pfx"), TestKeyFixtures.passwordFor("user1").toCharArray());
        assertThat(keyStore.size()).isPositive();
    }

    @Test
    void differentUsersGetDifferentKeys(@TempDir Path dir) throws Exception {
        TestKeyFixtures.generateTo("user1", dir);
        TestKeyFixtures.generateTo("user3", dir);

        assertThat(Files.readAllBytes(dir.resolve("user1.pem"))).isNotEqualTo(Files.readAllBytes(dir.resolve("user3.pem")));
    }
}
