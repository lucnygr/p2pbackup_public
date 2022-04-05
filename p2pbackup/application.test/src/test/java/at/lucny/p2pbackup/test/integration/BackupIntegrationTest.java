package at.lucny.p2pbackup.test.integration;

import at.lucny.p2pbackup.backup.dto.BackupIndex;
import at.lucny.p2pbackup.backup.dto.PathDataVersion;
import at.lucny.p2pbackup.backup.service.BackupService;
import at.lucny.p2pbackup.backup.support.BackupConstants;
import at.lucny.p2pbackup.backup.support.BackupUtils;
import at.lucny.p2pbackup.core.domain.*;
import at.lucny.p2pbackup.core.service.BlockEncryptionService;
import at.lucny.p2pbackup.core.support.HashUtils;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.localstorage.service.LocalStorageServiceImpl;
import at.lucny.p2pbackup.verification.domain.VerificationValue;
import at.lucny.p2pbackup.verification.service.VerificationValueService;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles({"integrationtest", "integrationtest_user1"})
@ContextConfiguration(initializers = BaseSingleApplicationIntegrationTest.ConfigDirAndRootDirectoryContextInitializer.class)
class BackupIntegrationTest extends BaseSingleApplicationIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private LocalStorageService localStorageService;

    @Autowired
    private BlockEncryptionService blockEncryptionService;

    @Autowired
    private VerificationValueService verificationValueService;

    @Value("classpath:testfile1.txt")
    private Resource testfile1;

    @Value("classpath:testfile2.txt")
    private Resource testfile2;

    private final HashUtils hashUtils = new HashUtils();

    @AfterEach
    void afterEach() {
        this.verificationValueRepository.deleteAll();
        this.cloudUploadRepository.deleteAll();
        this.pathDataRepository.deleteAll();
        this.blockMetaDataRepository.deleteAll();
    }

    private RootDirectory getConfiguredRootDirectory() {
        List<RootDirectory> rootDirectoryList = this.rootDirectoryRepository.findAll();
        assertThat(rootDirectoryList).hasSize(1);
        return rootDirectoryList.get(0);
    }

    @DisplayName("backups two new files, creates for each file one version-bmd and data-bmd, saves them in local storage and generates per bmd 12 verification values")
    @Test
    void testBackupRootDirectory() throws IOException {
        RootDirectory rootDirectory = this.getConfiguredRootDirectory();

        Path directory = createDirectory(getDataDir().resolve("subdir"));
        Path file1Path = directory.resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), file1Path);
        Path file2Path = getDataDir().resolve("testfile2.txt");
        Files.copy(this.testfile2.getFile().toPath(), file2Path);

        Set<String> versionBlockIds = this.backupService.backupRootDirectory(rootDirectory);
        assertThat(versionBlockIds).hasSize(2);

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll().stream().sorted(Comparator.comparing(PathData::getPath)).toList();
            assertThat(pathDataList).hasSize(2);

            // testfile1.txt
            PathData pathData = pathDataList.get(0);
            assertThat(pathData.getId()).isNotNull();
            assertThat(pathData.getDateCreated()).isNotNull();
            assertThat(pathData.getDateUpdated()).isNotNull().isEqualTo(pathData.getDateCreated());
            assertThat(pathData.getRootDirectory()).isEqualTo(rootDirectory);
            assertThat(pathData.getPath()).isEqualTo(getDataDir().relativize(file1Path).toString());

            assertThat(pathData.getVersions()).hasSize(1);
            PathVersion version = pathData.getVersions().stream().findFirst().get();
            assertThat(versionBlockIds).contains(version.getVersionBlock().getId());
            this.assertPathVersion(version, file1Path);
            this.assertBlockMetaData(version.getVersionBlock(), null);

            assertThat(version.getBlocks()).as("the file is small enough to contain only one data block").hasSize(1);
            BlockMetaData blockMetaData = version.getBlocks().get(0);
            this.assertBlockMetaData(blockMetaData, this.hashUtils.generateHashForFile(file1Path)); // hash for block and file are the same

            byte[] persistedVersionBlock = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(version.getVersionBlock(), 12);
            this.assertPathDataVersion(persistedVersionBlock, rootDirectory, pathData, version);
            byte[] persistedPlainText = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(blockMetaData, 12);
            assertThat(persistedPlainText).isEqualTo(this.readFile(file1Path));

            // testfile2.txt
            pathData = pathDataList.get(1);
            assertThat(pathData.getId()).isNotNull();
            assertThat(pathData.getDateCreated()).isNotNull();
            assertThat(pathData.getDateUpdated()).isNotNull().isEqualTo(pathData.getDateCreated());
            assertThat(pathData.getRootDirectory()).isEqualTo(rootDirectory);
            assertThat(pathData.getPath()).isEqualTo(getDataDir().relativize(file2Path).toString());

            assertThat(pathData.getVersions()).hasSize(1);
            version = pathData.getVersions().stream().findFirst().get();
            assertThat(versionBlockIds).contains(version.getVersionBlock().getId());
            this.assertPathVersion(version, file2Path);
            this.assertBlockMetaData(version.getVersionBlock(), null);

            assertThat(version.getBlocks()).as("the file is small enough to contain only one data block").hasSize(1);
            blockMetaData = version.getBlocks().get(0);
            this.assertBlockMetaData(blockMetaData, this.hashUtils.generateHashForFile(file2Path)); // hash for block and file are the same

            persistedVersionBlock = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(version.getVersionBlock(), 12);
            this.assertPathDataVersion(persistedVersionBlock, rootDirectory, pathData, version);
            persistedPlainText = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(blockMetaData, 12);
            assertThat(persistedPlainText).isEqualTo(this.readFile(file2Path));

            long countBlocks = this.blockMetaDataRepository.count();
            assertThat(countBlocks).as("for each file should exist one version-block and one data-block, but there are %s meta-data-blocks", countBlocks).isEqualTo(4);
            countBlocks = this.cloudUploadRepository.count();
            assertThat(countBlocks).as("for each file should exist one version-block and one data-block, but there are %s local-backup-blocks", countBlocks).isEqualTo(4);
            countBlocks = this.verificationValueRepository.count();
            assertThat(countBlocks).as("for each bmd should exist 12 verification-values, but there are %s verification-values", countBlocks).isEqualTo(4 * 12);
        });
    }

    private void assertBlockMetaData(BlockMetaData bmd, String hash) {
        assertThat(bmd).isNotNull();
        assertThat(bmd.getId()).isNotNull();
        assertThat(bmd.getDateCreated()).isNotNull();
        assertThat(bmd.getDateUpdated()).isNotNull().isEqualTo(bmd.getDateCreated());
        assertThat(bmd.getHash()).isEqualTo(hash);
        assertThat(bmd.getLocations()).as("directly after backup there should not be any backup-locations").isEmpty();
    }

    @SneakyThrows
    private byte[] assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(BlockMetaData bmd, int nrOfVerificationValues) {
        Optional<CloudUpload> optionalCloudUpload = this.cloudUploadRepository.findByBlockMetaDataId(bmd.getId());
        assertThat(optionalCloudUpload).isNotNull().isPresent();
        assertThat(optionalCloudUpload.get().getId()).isNotNull();
        assertThat(optionalCloudUpload.get().getDateCreated()).isNotNull();
        assertThat(optionalCloudUpload.get().getDateUpdated()).isNotNull().isEqualTo(optionalCloudUpload.get().getDateCreated());
        assertThat(optionalCloudUpload.get().getBlockMetaData().getId()).isEqualTo(bmd.getId());
        assertThat(optionalCloudUpload.get().getProviderId()).as("directly after backup there should be no provider-id because the block is not uploaded yet").isNull();
        assertThat(optionalCloudUpload.get().getShareUrl()).as("directly after backup there should be no sahre-url because the block is not uploaded yet").isNull();

        Optional<Path> pathToLocalBackupBlock = this.localStorageService.loadFromLocalStorage(bmd.getId());
        assertThat(pathToLocalBackupBlock).isPresent();
        assertThat(optionalCloudUpload.get().getMacSecret()).isNotNull();
        assertThat(optionalCloudUpload.get().getEncryptedBlockMac()).isEqualTo(this.hashUtils.generateMacForFile(pathToLocalBackupBlock.get(), optionalCloudUpload.get().getMacSecret()));

        List<VerificationValue> values = this.verificationValueRepository.findByBlockMetaDataIdOrderByIdAsc(bmd.getId());
        assertThat(values).hasSize(nrOfVerificationValues);
        for (VerificationValue value : values) {
            assertThat(value.getId()).isNotNull();
            assertThat(value.getBlockMetaData().getId()).isEqualTo(bmd.getId());
            assertThat(value.getHash()).isEqualTo(this.verificationValueService.generateHashFromChallenge(pathToLocalBackupBlock.get(), value.getId()).get());
        }

        return this.decryptBlock(pathToLocalBackupBlock.get(), bmd.getId());
    }

    @SneakyThrows
    private byte[] decryptBlock(Path pathToBlock, String bmdId) {
        byte[] encryptedData = Files.readAllBytes(pathToBlock);
        ByteBuffer plainDataBuffer = ByteBuffer.allocate(1024 * 1024);
        this.blockEncryptionService.decrypt(ByteBuffer.wrap(encryptedData), bmdId.getBytes(StandardCharsets.UTF_8), plainDataBuffer);
        byte[] plainData = new byte[plainDataBuffer.remaining()];
        plainDataBuffer.get(plainData);
        return plainData;
    }

    @DisplayName("changes and then deletes a file and generates a new version each time")
    @Test
    void testBackupRootDirectory_changeAndDeleteFile() throws IOException {
        RootDirectory rootDirectory = this.getConfiguredRootDirectory();

        Path directory = createDirectory(getDataDir().resolve("subdir"));
        Path filePath = directory.resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), filePath);

        Set<String> versionBlockIds = this.backupService.backupRootDirectory(rootDirectory);

        PathVersion version1 = new TransactionTemplate(this.txManager).execute(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll();
            assertThat(pathDataList).hasSize(1);
            PathData pathData = pathDataList.get(0);

            assertThat(pathData.getVersions()).hasSize(1);
            PathVersion version = pathData.getVersions().stream().findFirst().get();
            this.assertPathVersion(version, filePath);
            return version;
        });
        assertThat(versionBlockIds).hasSize(1).containsExactly(version1.getVersionBlock().getId());

        Files.writeString(filePath, "additions", StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        versionBlockIds = this.backupService.backupRootDirectory(rootDirectory);

        PathVersion version2 = new TransactionTemplate(this.txManager).execute(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll();
            assertThat(pathDataList).hasSize(1);
            PathData pathData = pathDataList.get(0);

            List<PathVersion> versions = pathData.getVersions().stream().sorted(Comparator.comparing(PathVersion::getDate)).toList();
            assertThat(versions).hasSize(2);
            assertThat(versions.get(0)).isEqualTo(version1);

            PathVersion newVersion = versions.get(1);
            this.assertPathVersion(newVersion, filePath);
            assertThat(newVersion.getHash()).isNotEqualTo(version1.getHash());

            byte[] persistedPathDataVersion = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(newVersion.getVersionBlock(), 12);
            this.assertPathDataVersion(persistedPathDataVersion, rootDirectory, pathData, newVersion);

            return newVersion;
        });
        assertThat(versionBlockIds).hasSize(1).containsExactly(version2.getVersionBlock().getId());

        Files.deleteIfExists(filePath);
        versionBlockIds = this.backupService.backupRootDirectory(rootDirectory);

        PathVersion version3 = new TransactionTemplate(this.txManager).execute(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll();
            assertThat(pathDataList).hasSize(1);
            PathData pathData = pathDataList.get(0);

            List<PathVersion> versions = pathData.getVersions().stream().sorted(Comparator.comparing(PathVersion::getDate)).toList();
            assertThat(versions).hasSize(3);
            assertThat(versions.get(0)).isEqualTo(version1);
            assertThat(versions.get(1)).isEqualTo(version2);

            PathVersion newVersion = versions.get(2);
            assertThat(newVersion.getId()).isNotNull();
            assertThat(newVersion.getDate()).isNotNull(); // TODO hier passt die zeit noch nicht .isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());
            assertThat(newVersion.getDeleted()).isTrue();
            assertThat(newVersion.getHash()).isNull();

            byte[] persistedPathDataVersion = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(newVersion.getVersionBlock(), 12);
            this.assertPathDataVersion(persistedPathDataVersion, rootDirectory, pathData, newVersion);
            return newVersion;
        });
        assertThat(versionBlockIds).containsExactly(version3.getVersionBlock().getId());

        Files.copy(this.testfile1.getFile().toPath(), filePath);
        versionBlockIds = this.backupService.backupRootDirectory(rootDirectory);

        PathVersion version4 = new TransactionTemplate(this.txManager).execute(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll();
            assertThat(pathDataList).hasSize(1);
            PathData pathData = pathDataList.get(0);

            List<PathVersion> versions = pathData.getVersions().stream().sorted(Comparator.comparing(PathVersion::getDate)).toList();
            assertThat(versions).hasSize(4);
            assertThat(versions.get(0)).isEqualTo(version1);
            assertThat(versions.get(1)).isEqualTo(version2);
            assertThat(versions.get(2)).isEqualTo(version3);

            PathVersion newVersion = versions.get(3);
            this.assertPathVersion(newVersion, filePath);
            assertThat(newVersion.getHash()).isEqualTo(version1.getHash());

            byte[] persistedPathDataVersion = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(newVersion.getVersionBlock(), 12);
            this.assertPathDataVersion(persistedPathDataVersion, rootDirectory, pathData, newVersion);
            return newVersion;
        });
        assertThat(versionBlockIds).hasSize(1).containsExactly(version4.getVersionBlock().getId());
    }

    private void assertPathVersion(PathVersion version, Path filePath) {
        assertThat(version.getId()).isNotNull();
        assertThat(version.getDate()).isNotNull(); // TODO hier passt die zeit noch nicht .isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());
        assertThat(version.getDeleted()).isFalse();
        assertThat(version.getHash()).isEqualTo(this.hashUtils.generateHashForFile(filePath));
    }

    @DisplayName("backups two files with the same content. this should generate the data-blocks only once")
    @Test
    void testBackupRootDirectory_twoFilesWithSameContent() throws IOException {
        RootDirectory rootDirectory = this.getConfiguredRootDirectory();

        Path filePath1 = getDataDir().resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), filePath1);
        Path filePath2 = getDataDir().resolve("testfile2.txt");
        Files.copy(this.testfile1.getFile().toPath(), filePath2);

        this.backupService.backupRootDirectory(rootDirectory);

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll().stream().sorted(Comparator.comparing(PathData::getPath)).toList();
            assertThat(pathDataList).hasSize(2);

            PathData pathData1 = pathDataList.get(0);
            assertThat(pathData1.getPath()).isEqualTo(getDataDir().relativize(filePath1).toString());
            PathData pathData2 = pathDataList.get(1);
            assertThat(pathData2.getPath()).isEqualTo(getDataDir().relativize(filePath2).toString());
            assertThat(pathData1).isNotEqualTo(pathData2);

            assertThat(pathData1.getVersions()).hasSize(1);
            PathVersion version1 = pathData1.getVersions().stream().findFirst().get();
            this.assertPathVersion(version1, filePath1);
            this.assertBlockMetaData(version1.getVersionBlock(), null);

            assertThat(pathData2.getVersions()).hasSize(1);
            PathVersion version2 = pathData2.getVersions().stream().findFirst().get();
            this.assertPathVersion(version2, filePath2);
            this.assertBlockMetaData(version2.getVersionBlock(), null);
            assertThat(version1).isNotEqualTo(version2);

            assertThat(version1.getBlocks()).as("the file is small enough to contain only one data block").hasSize(1);
            BlockMetaData blockMetaData = version1.getBlocks().get(0);
            this.assertBlockMetaData(blockMetaData, this.hashUtils.generateHashForFile(filePath1)); // hash for block and file are the same

            assertThat(version2.getBlocks()).as("the file is small enough to contain only one data block").hasSize(1);
            BlockMetaData blockMetaData2 = version2.getBlocks().get(0);
            assertThat(blockMetaData).usingRecursiveComparison().as("blocks of both files must be the same").isEqualTo(blockMetaData2);

            assertThat(this.cloudUploadRepository.count()).as("two version-blocks and one data-block should exist").isEqualTo(3);
            byte[] persistedVersionBlock = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(version1.getVersionBlock(), 12);
            this.assertPathDataVersion(persistedVersionBlock, rootDirectory, pathData1, version1);
            persistedVersionBlock = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(version2.getVersionBlock(), 12);
            this.assertPathDataVersion(persistedVersionBlock, rootDirectory, pathData2, version2);
            byte[] persistedPlainText = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(blockMetaData, 12);
            assertThat(persistedPlainText).isEqualTo(this.readFile(filePath1));
        });
    }

    @DisplayName("backup a file that contains multiple data-blocks")
    @Test
    void testBackupRootDirectory_multipleBlocksInFile() throws IOException {
        RootDirectory rootDirectory = this.getConfiguredRootDirectory();

        Path file1Path = getDataDir().resolve("testfile_big.txt");
        List<byte[]> content = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            byte[] bytes = new byte[100 * 1024];
            BackupUtils.RANDOM.nextBytes(bytes);
            Files.write(file1Path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            content.add(bytes);
        }

        this.backupService.backupRootDirectory(rootDirectory);

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll().stream().sorted(Comparator.comparing(PathData::getPath)).toList();
            assertThat(pathDataList).hasSize(1);
            PathData pathData = pathDataList.get(0);
            assertThat(pathData.getPath()).isEqualTo(getDataDir().relativize(file1Path).toString());

            assertThat(pathData.getVersions()).hasSize(1);
            PathVersion version = pathData.getVersions().stream().findFirst().get();
            this.assertPathVersion(version, file1Path);
            this.assertBlockMetaData(version.getVersionBlock(), null);

            assertThat(version.getBlocks()).as("the file should have %s data blocks, but has %s", content.size(), version.getBlocks()).hasSameSizeAs(content);
            for (int i = 0; i < content.size(); i++) {
                BlockMetaData bmd1 = version.getBlocks().get(i);
                this.assertBlockMetaData(bmd1, this.hashUtils.generateBlockHash(content.get(i)));
                byte[] plainData = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(bmd1, 12);
                assertThat(plainData).isEqualTo(content.get(i));
            }
            byte[] persistedVersionBlock = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(version.getVersionBlock(), 12);
            this.assertPathDataVersion(persistedVersionBlock, rootDirectory, pathData, version);

            long countLocalBackupBlocks = this.cloudUploadRepository.count();
            assertThat(countLocalBackupBlocks).as("there should be one version-block and %s data-blocks, but there are %s local-backup-blocks", content.size(), countLocalBackupBlocks).isEqualTo(content.size() + 1);
        });
    }

    @DisplayName("backup up an unchanged file should not modify any metadata")
    @Test
    void testBackupRootDirectory_unchangedFile() throws IOException {
        RootDirectory rootDirectory = this.getConfiguredRootDirectory();

        Path file1Path = getDataDir().resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), file1Path);

        this.backupService.backupRootDirectory(rootDirectory);

        PathVersion savedVersion = new TransactionTemplate(this.txManager).execute(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll().stream().sorted(Comparator.comparing(PathData::getPath)).toList();
            assertThat(pathDataList).hasSize(1);

            // testfile1.txt
            PathData pathData = pathDataList.get(0);
            assertThat(pathData.getPath()).isEqualTo(getDataDir().relativize(file1Path).toString());

            assertThat(pathData.getVersions()).hasSize(1);
            PathVersion version = pathData.getVersions().stream().findFirst().get();
            this.assertPathVersion(version, file1Path);
            this.assertBlockMetaData(version.getVersionBlock(), null);

            assertThat(version.getBlocks()).as("the file is small enough to contain only one data block").hasSize(1);
            BlockMetaData blockMetaData = version.getBlocks().get(0);
            this.assertBlockMetaData(blockMetaData, this.hashUtils.generateHashForFile(file1Path)); // hash for block and file are the same

            return version;
        });

        this.backupService.backupRootDirectory(rootDirectory);

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            List<PathData> pathDataList = this.pathDataRepository.findAll().stream().sorted(Comparator.comparing(PathData::getPath)).toList();
            assertThat(pathDataList).hasSize(1);

            // testfile1.txt
            PathData pathData = pathDataList.get(0);
            assertThat(pathData.getPath()).isEqualTo(getDataDir().relativize(file1Path).toString());

            assertThat(pathData.getVersions()).hasSize(1);
            PathVersion version = pathData.getVersions().stream().findFirst().get();
            assertThat(version).isEqualTo(savedVersion);
            assertThat(version.getBlocks()).containsExactlyInAnyOrderElementsOf(savedVersion.getBlocks());
        });
    }

    private void assertPathDataVersion(byte[] data, RootDirectory rootDirectory, PathData pathData, PathVersion version) {
        try {
            PathDataVersion pathDataVersion = PathDataVersion.parseFrom(data);
            assertThat(pathDataVersion).isNotNull();
            assertThat(pathDataVersion.getRootDirectoryId()).isEqualTo(rootDirectory.getId());
            assertThat(pathDataVersion.getPath()).isEqualTo(pathData.getPath());
            assertThat(pathDataVersion.getDate()).isEqualTo(version.getDate().toInstant(ZoneOffset.UTC).toEpochMilli());
            assertThat(pathDataVersion.getDeleted()).isEqualTo(version.getDeleted());
            if (version.getHash() != null) {
                assertThat(pathDataVersion.getHash()).isEqualTo(version.getHash());
            } else {
                assertThat(pathDataVersion.getHash()).isEmpty();
            }
            assertThat(pathDataVersion.getBlockIdsList()).containsExactlyElementsOf(version.getBlocks().stream().map(BlockMetaData::getId).toList());
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }

    @DisplayName("backup should generate verification values for blocks with less 6 or less verification-values")
    @Test
    void testBackupRootDirectory_generateNewVerificationValues() throws IOException {
        BlockMetaData bmd = new BlockMetaData(this.hashUtils.generateHashForFile(this.testfile1.getFile().toPath()));
        bmd.addDataLocation(new DataLocation(bmd, "user1", LocalDateTime.now()));
        bmd.addDataLocation(new DataLocation(bmd, "user2", LocalDateTime.now()));
        this.blockMetaDataRepository.save(bmd);

        List<VerificationValue> values = IntStream.range(1, 7).mapToObj(i -> new VerificationValue("id" + i, bmd, "hash" + i)).toList();
        this.verificationValueRepository.saveAll(values);

        Path file1Path = getDataDir().resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), file1Path);
        this.backupService.backupRootDirectory(this.getConfiguredRootDirectory());

        // backup process should create missing verification values
        List<VerificationValue> newValues = this.verificationValueRepository.findByBlockMetaDataIdOrderByIdAsc(bmd.getId());
        assertThat(newValues).hasSize(12).containsAll(values);

        // backup process should not save block in local-storage because we have enough data locations
        Optional<Path> localBackupBlock = this.localStorageService.loadFromLocalStorage(bmd.getId());
        assertThat(localBackupBlock).isNotPresent();
    }

    @DisplayName("backup should not generate verification-values if more than 6 verification-values are available")
    @Test
    void testBackupRootDirectory_dontGenerateVerificationValues() throws IOException {
        BlockMetaData bmd = new BlockMetaData(this.hashUtils.generateHashForFile(this.testfile1.getFile().toPath()));
        bmd.addDataLocation(new DataLocation(bmd, "user1", LocalDateTime.now()));
        bmd.addDataLocation(new DataLocation(bmd, "user2", LocalDateTime.now()));
        this.blockMetaDataRepository.save(bmd);

        List<VerificationValue> values = IntStream.range(1, 8).mapToObj(i -> new VerificationValue("id" + i, bmd, "hash" + i)).toList();
        this.verificationValueRepository.saveAll(values);

        Path file1Path = getDataDir().resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), file1Path);
        this.backupService.backupRootDirectory(this.getConfiguredRootDirectory());

        // backup process should create missing verification values
        assertThat(this.verificationValueRepository.findByBlockMetaDataIdOrderByIdAsc(bmd.getId())).containsExactlyInAnyOrderElementsOf(values);

        // backup process should not save block in local-storage because we have enough data locations
        Optional<Path> localBackupBlock = this.localStorageService.loadFromLocalStorage(bmd.getId());
        assertThat(localBackupBlock).isNotPresent();
    }

    @DisplayName("backup should generate a backup-block and persist the block in local storage if there are not enough backup locations")
    @Test
    void testBackupRootDirectory_persistBlockInLocalStorage() throws IOException {
        BlockMetaData bmd = this.blockMetaDataRepository.save(new BlockMetaData(this.hashUtils.generateHashForFile(this.testfile1.getFile().toPath())));

        List<VerificationValue> values = IntStream.range(1, 7).mapToObj(i -> new VerificationValue("id" + i, bmd, "hash" + i)).toList();
        this.verificationValueRepository.saveAll(values);

        Path file1Path = getDataDir().resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), file1Path);
        this.backupService.backupRootDirectory(this.getConfiguredRootDirectory());

        // backup process should generate a local backup block and persist the data block
        Optional<Path> localBackupBlock = this.localStorageService.loadFromLocalStorage(bmd.getId());
        assertThat(localBackupBlock).isPresent();

        Optional<CloudUpload> cloudUpload = this.cloudUploadRepository.findByBlockMetaDataId(bmd.getId());
        assertThat(cloudUpload).isPresent();
        assertThat(cloudUpload.get().getBlockMetaData().getId()).isEqualTo(bmd.getId());
        assertThat(this.p2PBackupProperties.getStorageDir().resolve(LocalStorageServiceImpl.FOLDER_BACKUP).resolve(bmd.getId())).exists();
    }

    @DisplayName("backups two new files and creates a new backup-index for them. after changing and deleting files backups again and creates new backup-index")
    @Test
    void testBackup() throws IOException {
        Path directory = createDirectory(getDataDir().resolve("subdir"));
        Path file1Path = directory.resolve("testfile1.txt");
        Files.copy(this.testfile1.getFile().toPath(), file1Path);
        Path file2Path = getDataDir().resolve("testfile2.txt");
        Files.copy(this.testfile2.getFile().toPath(), file2Path);

        this.backupService.backup();

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            List<String> versionBlockIds = this.pathVersionRepository.findAll().stream().map(PathVersion::getVersionBlock).map(BlockMetaData::getId).toList();

            List<BlockMetaData> indexBlocks = this.blockMetaDataRepository.findAllByIdLike(BackupConstants.BACKUP_INDEX_BLOCK_PREFIX + "%", Pageable.unpaged());
            assertThat(indexBlocks).hasSize(1);
            BlockMetaData indexBlock = indexBlocks.get(0);
            this.assertBlockMetaData(indexBlock, null);

            byte[] persistedVersionBlock = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(indexBlock, 12);
            this.assertBackupIndexBlock(persistedVersionBlock, versionBlockIds);
        });

        Files.writeString(file1Path, "additions", StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        Files.delete(file2Path);

        this.backupService.backup();

        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
            List<PathVersion> version = this.pathDataRepository.findAll().stream().flatMap(p -> p.getVersions().stream()).sorted(Comparator.comparing(PathVersion::getDate).reversed()).toList();
            assertThat(version).hasSize(4);
            List<String> versionBlockIds = new ArrayList<>();
            versionBlockIds.add(version.get(0).getVersionBlock().getId());
            versionBlockIds.add(version.get(1).getVersionBlock().getId());

            List<BlockMetaData> indexBlocks = this.blockMetaDataRepository.findAllByIdLike(BackupConstants.BACKUP_INDEX_BLOCK_PREFIX + "%", PageRequest.of(0, 100, Sort.Direction.DESC, "id"));
            assertThat(indexBlocks).hasSize(2);
            BlockMetaData indexBlock = indexBlocks.get(0);
            this.assertBlockMetaData(indexBlock, null);

            byte[] persistedVersionBlock = this.assertLocalSavedDataAndCloudUploadEntryAndVerificationValues(indexBlock, 12);
            this.assertBackupIndexBlock(persistedVersionBlock, versionBlockIds);
        });
    }

    private void assertBackupIndexBlock(byte[] persistedBackupIndexBlock, List<String> blockVersionIds) {
        try {
            BackupIndex backupIndex = BackupIndex.parseFrom(persistedBackupIndexBlock);
            assertThat(Instant.ofEpochMilli(backupIndex.getDate())).isAfter(Instant.now().minusSeconds(5));
            assertThat(backupIndex.getVersionBlockIdsList()).containsExactlyInAnyOrderElementsOf(blockVersionIds);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }

}
