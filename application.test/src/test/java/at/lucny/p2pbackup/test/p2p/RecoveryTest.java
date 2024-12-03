package at.lucny.p2pbackup.test.p2p;

import at.lucny.p2pbackup.application.service.BackupAgent;
import at.lucny.p2pbackup.application.service.CloudUploadAgent;
import at.lucny.p2pbackup.application.service.DistributionAgent;
import at.lucny.p2pbackup.core.repository.*;
import at.lucny.p2pbackup.restore.domain.RecoverBackupIndex;
import at.lucny.p2pbackup.restore.repository.RecoverBackupIndexRepository;
import at.lucny.p2pbackup.restore.service.RecoveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/*
@ExtendWith(SpringExtension.class)
class RecoveryTest extends BaseP2PTest {

    private byte[] contentOfBigFile;

    private void setupData() throws Exception {
        this.startContextForUser1();
        this.startContextForUser2();
        this.startContextForUser3();
        this.copyFileToUserDataDir("user1", "testfile1.txt");
        this.copyFileToUserDataDir("user1", "testfile2.txt");
        this.copyFileToUserDataDir("user1", "testfile3.txt");

        Path bigFile = createDirectory(getDataDir("user1").resolve("subdir")).resolve("testfile_big.txt");
        Files.write(bigFile, contentOfBigFile, StandardOpenOption.CREATE);

        int nrOfBlocks = 6 + 1 + contentOfBigFile.length / (BLOCK_SIZE) + 1;
        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(nrOfBlocks));
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(nrOfBlocks);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        await().untilAsserted(() -> assertThat(ctxUser1.getBean(CloudUploadRepository.class).count()).isZero());
    }

    @BeforeEach
    void beforeEach() throws Exception {
        this.contentOfBigFile = generateBytes(15);
    }

    @AfterEach
    void afterEach() throws IOException {
        super.afterEach();
    }

    @DisplayName("restore should restore the full backuped directory to a target directory")
    @Test
    void testRestore() throws Exception {
        this.setupData();

        this.stopContextForUser1();
        this.startContextForUser1();

        // DB shoult be empty
        assertThat(this.ctxUser1.getBean(RootDirectoryRepository.class).count()).isZero();
        assertThat(this.ctxUser1.getBean(PathDataRepository.class).count()).isZero();
        assertThat(this.ctxUser1.getBean(PathVersionRepository.class).count()).isZero();
        assertThat(this.ctxUser1.getBean(BlockMetaDataRepository.class).count()).isZero();

        RecoveryService recoveryService1 = this.ctxUser1.getBean(RecoveryService.class);
        recoveryService1.requestBackupIndex();

        RecoverBackupIndexRepository recoverBackupIndexRepository1 = this.ctxUser1.getBean(RecoverBackupIndexRepository.class);

        await().untilAsserted(() -> {
            assertThat(recoverBackupIndexRepository1.count()).isOne();
        });

        RecoverBackupIndex index = recoverBackupIndexRepository1.findAll().get(0);
        recoveryService1.startRecovery(index.getId());

        Path restoreDir = this.getRestoreDir("user1");
        System.setIn(new ByteArrayInputStream((restoreDir.toString() + "\n").getBytes(StandardCharsets.UTF_8)));




        RootDirectory rootDirectory = this.ctxUser1.getBean(RootDirectoryRepository.class).findAll().get(0);
        RestorationService restorationService1 = this.ctxUser1.getBean(RestorationService.class);
        restorationService1.beginRestore(rootDirectory, LocalDateTime.now(ZoneOffset.UTC), this.getRestoreDir("user1"));

        await().untilAsserted(() -> {
            restorationService1.restoreBlocks();
            assertThat(this.ctxUser1.getBean(RestorePathRepository.class).count()).isZero();
            assertThat(this.ctxUser1.getBean(RestorationStorageServiceImpl.class).getFiles()).isEmpty();
        });

        Path testfile1InRestoreDirectory = this.getRestoreDir("user1").resolve("testfile1.txt");
        assertThat(testfile1InRestoreDirectory).exists();
        assertThat(Files.readAllBytes(testfile1InRestoreDirectory)).containsExactly(Files.readAllBytes(this.getFileFromClasspath("testfile1.txt")));

        Path testfile2InRestoreDirectory = this.getRestoreDir("user1").resolve("testfile2.txt");
        assertThat(testfile2InRestoreDirectory).exists();
        assertThat(Files.readAllBytes(testfile2InRestoreDirectory)).containsExactly(Files.readAllBytes(this.getFileFromClasspath("testfile2.txt")));

        Path testfile3InRestoreDirectory = this.getRestoreDir("user1").resolve("testfile3.txt");
        assertThat(testfile3InRestoreDirectory).exists();
        assertThat(Files.readAllBytes(testfile3InRestoreDirectory)).containsExactly(Files.readAllBytes(this.getFileFromClasspath("testfile3.txt")));

        Path bigfileInRestoreDirectory = this.getRestoreDir("user1").resolve("subdir").resolve("testfile_big.txt");
        assertThat(bigfileInRestoreDirectory).exists();
        assertThat(Files.readAllBytes(bigfileInRestoreDirectory)).containsExactly(contentOfBigFile);
    }

}*/
