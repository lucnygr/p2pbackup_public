package at.lucny.p2pbackup.test.integration;

import at.lucny.p2pbackup.P2PBackupApplicationConfiguration;
import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.repository.*;
import at.lucny.p2pbackup.core.support.CryptoUtils;
import at.lucny.p2pbackup.localstorage.service.LocalStorageServiceImpl;
import at.lucny.p2pbackup.test.BaseTest;
import at.lucny.p2pbackup.verification.repository.VerificationValueRepository;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = P2PBackupApplicationConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class BaseSingleApplicationIntegrationTest extends BaseTest {

    @TempDir
    protected static Path TEMP_DIR;

    @Autowired
    protected PlatformTransactionManager txManager;

    @Autowired
    protected P2PBackupProperties p2PBackupProperties;

    @Autowired
    protected RootDirectoryRepository rootDirectoryRepository;

    @Autowired
    protected PathDataRepository pathDataRepository;

    @Autowired
    protected PathVersionRepository pathVersionRepository;

    @Autowired
    protected BlockMetaDataRepository blockMetaDataRepository;

    @Autowired
    protected CloudUploadRepository cloudUploadRepository;

    @Autowired
    protected VerificationValueRepository verificationValueRepository;

    @Autowired
    protected LocalStorageServiceImpl localStorageService;

    static Path getConfigDir() {
        return createDirectory(TEMP_DIR.resolve("CONFIG"));
    }

    static Path getDataDir() {
        return createDirectory(TEMP_DIR.resolve("DATA"));
    }

    static Path getStorageDir() {
        return createDirectory(TEMP_DIR.resolve("STORAGE"));
    }

    static Path getRestoreDir() {
        return createDirectory(TEMP_DIR.resolve("RESTORE"));
    }

    protected byte[] readFile(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void beforeEach_BaseSingleApplicationIntegrationTest() throws IOException {
        // reinitialize directories before each test
        this.localStorageService.initializeDirectories();
    }

    @BeforeClass
    static void beforeEachSetupKeystoreAndCertificate() throws NoSuchAlgorithmException, NoSuchProviderException, IOException {
        String keystorePassword = "passworduser1";
        Path pathToKeyStore = getConfigDir().resolve("user1.pfx");
        Path pathToCertificate = getConfigDir().resolve("user1.pem");
        // generate new keys for the user
        new CryptoUtils().generateAuthenticationKeys("user1", keystorePassword.toCharArray(), pathToKeyStore, pathToCertificate);
    }

    @AfterEach
    void afterEach_BaseSingleApplicationIntegrationTest() throws IOException {
        FileUtils.cleanDirectory(getConfigDir().toFile());
        FileUtils.cleanDirectory(getStorageDir().toFile());
        FileUtils.cleanDirectory(getDataDir().toFile());
        FileUtils.cleanDirectory(getRestoreDir().toFile());
    }

    static class ConfigDirContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            Map<String, String> properties = new HashMap<>();
            properties.put("at.lucny.p2p-backup.config-dir", getConfigDir().toString());
            properties.put("at.lucny.p2p-backup.storage-dir", getStorageDir().toString());
            properties.put("at.lucny.p2p-backup.keystore", "file:" + getConfigDir().resolve("user1.pfx").toString());
            properties.put("at.lucny.p2p-backup.password", "passworduser1");
            TestPropertyValues.of(properties).applyTo(applicationContext);
        }
    }

    static class ConfigDirAndRootDirectoryContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            Map<String, String> properties = new HashMap<>();
            properties.put("at.lucny.p2p-backup.config-dir", getConfigDir().toString());
            properties.put("at.lucny.p2p-backup.storage-dir", getStorageDir().toString());
            properties.put("at.lucny.p2p-backup.init.root-directories.0.name", "datadir");
            properties.put("at.lucny.p2p-backup.init.root-directories.0.path", getDataDir().toString());
            properties.put("at.lucny.p2p-backup.keystore", "file:" + getConfigDir().resolve("user1.pfx").toString());
            properties.put("at.lucny.p2p-backup.password", "passworduser1");
            TestPropertyValues.of(properties).applyTo(applicationContext);
        }
    }
}
