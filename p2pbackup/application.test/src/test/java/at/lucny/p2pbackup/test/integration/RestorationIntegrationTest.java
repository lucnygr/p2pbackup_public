package at.lucny.p2pbackup.test.integration;

import at.lucny.p2pbackup.backup.service.BackupService;
import at.lucny.p2pbackup.core.domain.PathVersion;
import at.lucny.p2pbackup.core.domain.RootDirectory;
import at.lucny.p2pbackup.core.repository.PathVersionRepository;
import at.lucny.p2pbackup.restore.domain.RestoreBlockData;
import at.lucny.p2pbackup.restore.domain.RestorePath;
import at.lucny.p2pbackup.restore.domain.RestoreType;
import at.lucny.p2pbackup.restore.repository.RestoreBlockDataRepository;
import at.lucny.p2pbackup.restore.repository.RestorePathRepository;
import at.lucny.p2pbackup.restore.service.RestorationService;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles({"integrationtest", "integrationtest_user1"})
@ContextConfiguration(initializers = BaseSingleApplicationIntegrationTest.ConfigDirAndRootDirectoryContextInitializer.class)
class RestorationIntegrationTest extends BaseSingleApplicationIntegrationTest {
    @Autowired
    private BackupService backupService;

    @Autowired
    private PathVersionRepository pathVersionRepository;

    @Autowired
    private RestorationService restorationService;

    @Autowired
    private RestorePathRepository restorePathRepository;

    @Autowired
    private RestoreBlockDataRepository restoreBlockDataRepository;

    @Autowired
    private PlatformTransactionManager txManager;


    @Value("classpath:testfile1.txt")
    private Resource testfile1;

    @Value("classpath:testfile2.txt")
    private Resource testfile2;

    @AfterEach
    void afterEach() {
        this.deleteRestorePathEntries();
        this.restoreBlockDataRepository.deleteAll();

        this.cloudUploadRepository.deleteAll();
        this.verificationValueRepository.deleteAll();
        this.pathDataRepository.deleteAll();
        this.blockMetaDataRepository.deleteAll();
    }

    private void deleteRestorePathEntries() {
        List<RestorePath> restorePaths = this.restorePathRepository.findAllFetchMissingBlocks();
        for (RestorePath path : restorePaths) {
            path.getMissingBlocks().clear();
            path = this.restorePathRepository.save(path);
            this.restorePathRepository.delete(path);
        }
    }

    private RootDirectory getConfiguredRootDirectory() {
        List<RootDirectory> rootDirectoryList = this.rootDirectoryRepository.findAll();
        assertThat(rootDirectoryList).hasSize(1);
        return rootDirectoryList.get(0);
    }

    @Test
    void testBeginRestore_withNewDirectory() throws Exception {
        RootDirectory rootDirectory = this.getConfiguredRootDirectory();

        Path directory = createDirectory(getDataDir().resolve("subdir"));
        Path file1Path = directory.resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), file1Path);
        Path file2Path = getDataDir().resolve("testfile2.txt");
        Files.copy(this.testfile2.getFile().toPath(), file2Path);

        this.backupService.backupRootDirectory(rootDirectory);
        this.restorationService.beginRestore(rootDirectory, LocalDateTime.now(ZoneOffset.UTC), getRestoreDir());

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            List<RestorePath> expectedRestorePaths = this.pathDataRepository.findAll().stream().map(pd -> {
                PathVersion version = pd.getVersions().iterator().next();
                RestorePath restorePath = new RestorePath(version, getRestoreDir().resolve(pd.getPath()).toString());
                restorePath.getMissingBlocks().addAll(version.getBlocks().stream().map(b -> new RestoreBlockData(b, RestoreType.RESTORE)).toList());
                return restorePath;
            }).sorted(Comparator.comparing(RestorePath::getPath)).toList();

            List<RestorePath> persistedRestorePaths = this.restorePathRepository.findAll().stream().sorted(Comparator.comparing(RestorePath::getPath)).toList();

            for (int i = 0; i < persistedRestorePaths.size(); i++) {
                assertThat(persistedRestorePaths.get(i).getId()).isNotNull();
                assertThat(persistedRestorePaths.get(i).getPath()).isEqualTo(expectedRestorePaths.get(i).getPath());
                assertThat(persistedRestorePaths.get(i).getPathVersion().getId()).isEqualTo(expectedRestorePaths.get(i).getPathVersion().getId());
                assertThat(persistedRestorePaths.get(i).getMissingBlocks().stream().map(rbd -> rbd.getBlockMetaData().getId()).toList())
                        .containsExactlyInAnyOrderElementsOf(expectedRestorePaths.get(i).getMissingBlocks().stream().map(rbd -> rbd.getBlockMetaData().getId()).toList());
            }
        });
    }


    @Test
    void testBeginRestore_withDirectoryWithChangedValues() throws Exception {
        RootDirectory rootDirectory = this.getConfiguredRootDirectory();

        Path directory = createDirectory(getDataDir().resolve("subdir"));
        Path file1Path = directory.resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), file1Path);
        Path file2Path = getDataDir().resolve("testfile2.txt");
        Files.copy(this.testfile2.getFile().toPath(), file2Path);
        this.backupService.backupRootDirectory(rootDirectory);

        Files.writeString(file1Path, "Testdata", StandardOpenOption.APPEND);
        Files.delete(file2Path);
        this.backupService.backupRootDirectory(rootDirectory);

        LocalDateTime restoreTimestamp = LocalDateTime.now(ZoneOffset.UTC);
        RestorePath expectedRestorePathAfterDelete = new TransactionTemplate(this.txManager).execute(status -> {
            Optional<RestorePath> path = this.pathDataRepository.findByRootDirectoryAndPath(rootDirectory, getDataDir().relativize(file1Path).toString())
                    .map(pd -> {
                        PathVersion version = pd.getVersions().stream().sorted(Comparator.comparing(PathVersion::getDate).reversed()).findFirst().get(); // get latest path version
                        RestorePath restorePath = new RestorePath(version, getRestoreDir().resolve(pd.getPath()).toString());
                        restorePath.getMissingBlocks().addAll(version.getBlocks().stream().map(b -> new RestoreBlockData(b, RestoreType.RESTORE)).toList());
                        return restorePath;
                    });
            assertThat(path).isPresent();
            return path.get();
        });

        Files.writeString(file1Path, "Testdata2", StandardOpenOption.APPEND);
        Files.copy(this.testfile2.getFile().toPath(), file2Path);
        this.backupService.backupRootDirectory(rootDirectory);

        this.restorationService.beginRestore(rootDirectory, restoreTimestamp, getRestoreDir());

        assertThat(this.restorePathRepository.count()).isEqualTo(1);

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            RestorePath persistedRestorePath = this.restorePathRepository.findAll().iterator().next();

            assertThat(persistedRestorePath.getId()).isNotNull();
            assertThat(persistedRestorePath.getPath()).isEqualTo(expectedRestorePathAfterDelete.getPath());
            assertThat(persistedRestorePath.getPathVersion().getId()).isEqualTo(expectedRestorePathAfterDelete.getPathVersion().getId());
            assertThat(persistedRestorePath.getMissingBlocks().stream().map(rbd -> rbd.getBlockMetaData().getId()).toList())
                    .containsExactlyInAnyOrderElementsOf(expectedRestorePathAfterDelete.getMissingBlocks().stream().map(rbd -> rbd.getBlockMetaData().getId()).toList());
        });

        this.deleteRestorePathEntries();
        this.restoreBlockDataRepository.deleteAll();

        this.restorationService.beginRestore(rootDirectory, LocalDateTime.now(ZoneOffset.UTC), getRestoreDir().resolve("subdir"));

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            for (Path path : Lists.newArrayList(file1Path, file2Path)) {
                Optional<RestorePath> expectedRestorePath = this.pathDataRepository.findByRootDirectoryAndPath(rootDirectory, getDataDir().relativize(path).toString())
                        .map(pd -> {
                            PathVersion version = pd.getVersions().stream().sorted(Comparator.comparing(PathVersion::getDate).reversed()).findFirst().get(); // get latest path version
                            RestorePath restorePath = new RestorePath(version, getRestoreDir().resolve("subdir").resolve(pd.getPath()).toString());
                            restorePath.getMissingBlocks().addAll(version.getBlocks().stream().map(b -> new RestoreBlockData(b, RestoreType.RESTORE)).toList());
                            return restorePath;
                        });
                assertThat(expectedRestorePath).isPresent();

                assertThat(this.restorePathRepository.count()).isEqualTo(2);
                RestorePath persistedRestorePath = this.restorePathRepository.findAll().stream().filter(p -> p.getPath().endsWith(getDataDir().relativize(path).toString())).findFirst().get();

                assertThat(persistedRestorePath.getId()).isNotNull();
                assertThat(persistedRestorePath.getPath()).isEqualTo(expectedRestorePath.get().getPath());
                assertThat(persistedRestorePath.getPathVersion().getId()).isEqualTo(expectedRestorePath.get().getPathVersion().getId());
                assertThat(persistedRestorePath.getMissingBlocks().stream().map(rbd -> rbd.getBlockMetaData().getId()).toList())
                        .containsExactlyInAnyOrderElementsOf(expectedRestorePath.get().getMissingBlocks().stream().map(rbd -> rbd.getBlockMetaData().getId()).toList());
            }
        });
    }

}
