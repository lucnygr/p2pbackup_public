package at.lucny.p2pbackup.test.p2p;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.application.service.BackupAgent;
import at.lucny.p2pbackup.application.service.CloudUploadAgent;
import at.lucny.p2pbackup.application.service.DistributionAgent;
import at.lucny.p2pbackup.cloud.filesystem.service.FilesystemStorageServiceImpl;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.DataLocation;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.CloudUploadRepository;
import at.lucny.p2pbackup.core.repository.DataLocationRepository;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.localstorage.service.LocalStorageServiceImpl;
import at.lucny.p2pbackup.network.dto.BackupBlockFailure;
import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.dto.ProtocolMessageWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
class BackupTest extends BaseP2PTest {

    @Test
    void testBackupToOtherPeers() throws IOException {
        this.startContextForUser1();
        this.startContextForUser2();
        this.copyFileToUserDataDir("user1", "testfile1.txt");

        P2PBackupProperties p2PBackupProperties1 = ctxUser1.getBean(P2PBackupProperties.class);
        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(3)); // 1 data-block + 1 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(3);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        BlockMetaDataRepository bmdRepository1 = ctxUser1.getBean(BlockMetaDataRepository.class);
        LocalStorageService localStorageService1 = ctxUser1.getBean(LocalStorageService.class);
        CloudUploadRepository cloudUploadRepository1 = ctxUser1.getBean(CloudUploadRepository.class);

        await().untilAsserted(() -> {
            List<BlockMetaData> bmds = bmdRepository1.findAllFetchLocations();
            assertThat(bmds).hasSize(3);

            LocalStorageService localStorageService2 = ctxUser2.getBean(LocalStorageService.class);
            for (BlockMetaData bmd : bmds) {
                // the blocks should be saved in the local-storage of user2
                Optional<Path> blockPath = localStorageService2.loadFromLocalStorage("user1", bmd.getId());
                assertThat(blockPath).isPresent();

                assertThat(bmd.getLocations()).hasSize(1);
                DataLocation location = bmd.getLocations().iterator().next();
                assertThat(location.getBlockMetaData().getId()).isEqualTo(bmd.getId());
                assertThat(location.getUserId()).isEqualTo("user2");
                assertThat(location.getVerified()).isBetween(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()), LocalDateTime.now(ZoneOffset.UTC));

                // the blocks should still be at user2 because there are not enough saving locations
                assertThat(cloudUploadRepository1.findByBlockMetaDataId(bmd.getId())).isPresent();
                assertThat(localStorageService1.loadFromLocalStorage(bmd.getId())).isPresent();
            }
        });

        this.startContextForUser3();

        ctxUser1.getBean(DistributionAgent.class).distribute();

        await().untilAsserted(() -> {
            assertThat(cloudUploadRepository1.count()).isZero();
            // the files from the cloud-storage should be deleted
            assertThat(ctxUser1.getBean(FilesystemStorageServiceImpl.class).getFiles()).isEmpty();
            // the files from the storage directory should be deleted
            assertThat(this.ctxUser1.getBean(LocalStorageServiceImpl.class).getFiles()).isEmpty();
        });

        List<BlockMetaData> bmds = bmdRepository1.findAll();
        assertThat(bmds).hasSize(3);

        LocalStorageService localStorageService3 = ctxUser3.getBean(LocalStorageService.class);
        for (BlockMetaData bmd : bmds) {
            // the blocks should be saved in the local-storage of user3
            Optional<Path> blockPath = localStorageService3.loadFromLocalStorage("user1", bmd.getId());
            assertThat(blockPath).isPresent();

            bmd = bmdRepository1.findByIdFetchLocations(bmd.getId()).get();
            assertThat(bmd.getLocations()).extracting(DataLocation::getUserId).containsExactlyInAnyOrder("user2", "user3");

            // the blocks should be deleted because they are now saved at user2 and user3
            assertThat(localStorageService1.loadFromLocalStorage(bmd.getId())).isNotPresent();
        }
    }

    @Test
    void testBackupToOtherPeers_requestBlockFromOtherPeerForRedistribution() throws IOException {
        this.startContextForUser1();
        this.startContextForUser2();
        this.startContextForUser3();
        this.copyFileToUserDataDir("user1", "testfile1.txt");

        P2PBackupProperties p2PBackupProperties1 = ctxUser1.getBean(P2PBackupProperties.class);
        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(3)); // 1 data-block + 1 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(3);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        BlockMetaDataRepository bmdRepository1 = ctxUser1.getBean(BlockMetaDataRepository.class);
        LocalStorageService localStorageService1 = ctxUser1.getBean(LocalStorageService.class);
        CloudUploadRepository cloudUploadRepository1 = ctxUser1.getBean(CloudUploadRepository.class);

        await().untilAsserted(() -> {
            List<BlockMetaData> bmds = bmdRepository1.findAll();
            assertThat(bmds).hasSize(3);

            for (BlockMetaData bmd : bmds) {
                // the blocks should be deleted because they are now saved at user2 and user3
                assertThat(localStorageService1.loadFromLocalStorage(bmd.getId())).isNotPresent();
            }
            assertThat(cloudUploadRepository1.count()).isZero();
        });

        // set a block as unverified on user2 to trigger deletion
        BlockMetaData bmd = bmdRepository1.findAll().get(0);
        DataLocation location = this.ctxUser1.getBean(DataLocationRepository.class).findByBlockMetaDataIdAndUserId(bmd.getId(), "user2").get();
        ctxUser1.getBean(DataLocationRepository.class).delete(location);

        LocalStorageService localStorageService2 = ctxUser2.getBean(LocalStorageService.class);
        Path pathToBlock = localStorageService2.loadFromLocalStorage("user1", bmd.getId()).get();
        Files.delete(pathToBlock);

        // request and verify that the missing block is fetched and stored locally
        ctxUser1.getBean(DistributionAgent.class).distribute();

        await().untilAsserted(() -> {
            assertThat(localStorageService1.loadFromLocalStorage(bmd.getId())).isPresent();
            assertThat(cloudUploadRepository1.findByBlockMetaDataId(bmd.getId())).isPresent();
        });

        // distribute missing block to user2
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(1);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        await().untilAsserted(() -> {
            assertThat(cloudUploadRepository1.count()).isZero();
            // the files from the cloud-storage should be deleted
            assertThat(ctxUser1.getBean(FilesystemStorageServiceImpl.class).getFiles()).isEmpty();
            // the files from the storage directory should be deleted
            assertThat(this.ctxUser1.getBean(LocalStorageServiceImpl.class).getFiles()).isEmpty();
        });

        DataLocation locationOnUser2 = this.ctxUser1.getBean(DataLocationRepository.class).findByBlockMetaDataIdAndUserId(bmd.getId(), "user2").get();
        assertThat(locationOnUser2.getBlockMetaData().getId()).isEqualTo(bmd.getId());
        assertThat(locationOnUser2.getUserId()).isEqualTo("user2");
        assertThat(locationOnUser2.getVerified()).isBetween(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()), LocalDateTime.now(ZoneOffset.UTC));

        // the blocks should be deleted because they are now saved at user2 and user3
        assertThat(localStorageService1.loadFromLocalStorage(bmd.getId())).isNotPresent();
    }

    @Test
    void testBackupToPeer_user1DoesntAllowToBackupDataToUser2() throws IOException {
        this.ctxUser1 = this.createApplication("user1");
        this.ctxUser2 = this.createApplication("user2");

        this.addUser(this.ctxUser1, "user2", true, false);
        this.addUser(this.ctxUser2, "user1", true, true);

        this.copyFileToUserDataDir("user1", "testfile1.txt");

        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(3));// 1 data-block + 1 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(3);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        BlockMetaDataRepository bmdRepository1 = ctxUser1.getBean(BlockMetaDataRepository.class);

        await().pollDelay(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(ctxUser1.getBean(KeepSentMessagesHandler.class).getMessages()).isEmpty();

            List<BlockMetaData> bmds = bmdRepository1.findAllFetchLocations();
            assertThat(bmds).hasSize(3);

            LocalStorageService localStorageService2 = ctxUser2.getBean(LocalStorageService.class);
            for (BlockMetaData bmd : bmds) {
                // the blocks should be saved in the local-storage of user2
                Optional<Path> blockPath = localStorageService2.loadFromLocalStorage("user1", bmd.getId());
                assertThat(blockPath).isNotPresent();
            }
        });
    }

    @Test
    void testBackupToPeer_user1NotAllowedToBackupDataToUser2() throws IOException {
        this.ctxUser1 = this.createApplication("user1");
        this.ctxUser2 = this.createApplication("user2");

        this.addUser(this.ctxUser1, "user2", true, true);
        this.addUser(this.ctxUser2, "user1", false, true);

        this.copyFileToUserDataDir("user1", "testfile1.txt");

        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(3));// 1 data-block + 1 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(3);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        BlockMetaDataRepository bmdRepository1 = ctxUser1.getBean(BlockMetaDataRepository.class);

        await().untilAsserted(() -> {
            assertThat(ctxUser1.getBean(KeepSentMessagesHandler.class).getMessages()).hasSize(3);
            for (ProtocolMessageWrapper message : ctxUser1.getBean(KeepSentMessagesHandler.class).getMessages()) {
                assertThat(message.protocolMessage()).isNotNull();
                assertThat(message.protocolMessage().getMessageCase()).isEqualTo(ProtocolMessage.MessageCase.BACKUPFAILURE);
                assertThat(message.protocolMessage().getBackupFailure().getType()).isEqualTo(BackupBlockFailure.BackupBlockFailureType.USER_NOT_ALLOWED);
            }

            List<BlockMetaData> bmds = bmdRepository1.findAllFetchLocations();
            assertThat(bmds).hasSize(3);

            LocalStorageService localStorageService2 = ctxUser2.getBean(LocalStorageService.class);
            for (BlockMetaData bmd : bmds) {
                // the blocks should be saved in the local-storage of user2
                Optional<Path> blockPath = localStorageService2.loadFromLocalStorage("user1", bmd.getId());
                assertThat(blockPath).isNotPresent();
            }
        });
    }
}
