package at.lucny.p2pbackup.test.integration;

import at.lucny.p2pbackup.backup.service.BackupService;
import at.lucny.p2pbackup.core.domain.RootDirectory;
import at.lucny.p2pbackup.core.repository.RootDirectoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles({"integrationtest", "integrationtest_user1"})
@ContextConfiguration(initializers = BaseSingleApplicationIntegrationTest.ConfigDirContextInitializer.class)
class BackupServiceIntegrationTest extends BaseSingleApplicationIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private RootDirectoryRepository rootDirectoryRepository;

    @AfterEach
    void afterEach() {
        this.rootDirectoryRepository.deleteAll();
    }

    @Test
    void testAddRootDirectory() {
        Optional<RootDirectory> rd = this.backupService.addRootDirectory("datadir", getDataDir().toString());
        assertThat(rd).isPresent();
        assertThat(rd.get().getId()).isNotNull();
        assertThat(rd.get().getDateCreated()).isNotNull();
        assertThat(rd.get().getDateUpdated()).isNotNull().isEqualTo(rd.get().getDateCreated());
        assertThat(rd.get().getName()).isEqualTo("datadir");
        assertThat(rd.get().getPath()).isEqualTo(getDataDir().toString());

        List<RootDirectory> rootDirectoryList = this.rootDirectoryRepository.findAll();
        assertThat(rootDirectoryList).hasSize(1);
        RootDirectory persistedRootDirectory = rootDirectoryList.get(0);
        assertThat(persistedRootDirectory).usingRecursiveComparison().ignoringFields("dateCreated", "dateUpdated").isEqualTo(rd.get());

        Optional<RootDirectory> sameRootDirectory = this.backupService.addRootDirectory("datadir2", getDataDir().toString());
        assertThat(sameRootDirectory).isEmpty();

        // persisted root directory is still the same
        rootDirectoryList = this.rootDirectoryRepository.findAll();
        assertThat(rootDirectoryList).hasSize(1);
        persistedRootDirectory = rootDirectoryList.get(0);
        assertThat(persistedRootDirectory).usingRecursiveComparison().ignoringFields("dateCreated", "dateUpdated").isEqualTo(rd.get());
    }

    @Test
    void testAddRootDirectory_alreadyAdded() {
        this.rootDirectoryRepository.save(new RootDirectory("name", getDataDir().toString()));

        Optional<RootDirectory> rd = this.backupService.addRootDirectory("name2", getDataDir().toString());
        assertThat(rd).isNotPresent();

        assertThat(this.rootDirectoryRepository.count()).isOne();
    }


    @Test
    void testAddRootDirectory_duplicateName() {
        this.rootDirectoryRepository.save(new RootDirectory("name", getDataDir().toString()));
        Optional<RootDirectory> rd = this.backupService.addRootDirectory("name", getDataDir().toString());
        assertThat(rd).isNotPresent();

        assertThat(this.rootDirectoryRepository.count()).isOne();
    }

    @Test
    void testAddRootDirectory_notExistingDirectory() {
        Optional<RootDirectory> rd = this.backupService.addRootDirectory("name", getDataDir().resolve("unknown").toString());
        assertThat(rd).isNotPresent();
        assertThat(this.rootDirectoryRepository.count()).isZero();
    }

    @Test
    void testGetRootDirectories() {
        RootDirectory directory = new RootDirectory("name", "path");
        directory = this.rootDirectoryRepository.save(directory);

        List<RootDirectory> rootDirectoryList = this.backupService.getRootDirectories();
        assertThat(rootDirectoryList).containsExactly(directory);
    }

    @Test
    void testGetRootDirectory() {
        RootDirectory directory = new RootDirectory("name", "path");
        directory = this.rootDirectoryRepository.save(directory);

        Optional<RootDirectory> rootDirectory = this.backupService.getRootDirectory("name");
        assertThat(rootDirectory).isPresent().contains(directory);
    }
}
