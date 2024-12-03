package at.lucny.p2pbackup.test.p2p;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.application.service.BackupAgent;
import at.lucny.p2pbackup.application.service.CloudUploadAgent;
import at.lucny.p2pbackup.application.service.DistributionAgent;
import at.lucny.p2pbackup.application.service.VerificationAgent;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.DataLocation;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.CloudUploadRepository;
import at.lucny.p2pbackup.core.repository.DataLocationRepository;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.verification.domain.ActiveVerificationValue;
import at.lucny.p2pbackup.verification.repository.ActiveVerificationValueRepository;
import at.lucny.p2pbackup.verification.repository.VerificationValueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.util.ResourceUtils;

import java.io.File;
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

class VerificationTest extends BaseP2PTest {

    @BeforeEach
    void beforeEach() {
        this.startContextForUser1();
        this.startContextForUser2();
    }

    @Test
    void testVerifyBlocksAtOtherPeers() throws IOException {
        Path dataDirUser1 = this.getDataDir("user1");
        File testfile1 = ResourceUtils.getFile("classpath:testfile1.txt");
        Files.copy(testfile1.toPath(), dataDirUser1.resolve("testfile1.txt"));
        File testfile2 = ResourceUtils.getFile("classpath:testfile2.txt");
        Files.copy(testfile2.toPath(), dataDirUser1.resolve("testfile2.txt"));

        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(5));// 2 data-block + 2 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(5);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        DataLocationRepository dataLocationRepository1 = ctxUser1.getBean(DataLocationRepository.class);
        P2PBackupProperties p2PBackupProperties1 = ctxUser1.getBean(P2PBackupProperties.class);

        await().untilAsserted(() -> assertThat(dataLocationRepository1.count()).as("for 5 blocks (2 path-version-blocks, 2 data-blocks, 1 backup-index-block) should be a data location saved").isEqualTo(5));

        // set the verification date of all locations in the past so we have to verify them
        List<DataLocation> oldLocations = dataLocationRepository1.findAll();
        for (DataLocation location : oldLocations) {
            assertThat(location.getVerified()).isBetween(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()), LocalDateTime.now(ZoneOffset.UTC));
            location.setVerified(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()));
            dataLocationRepository1.save(location);
        }

        ctxUser1.getBean(VerificationAgent.class).verify();
        VerificationValueRepository verificationValueRepository1 = ctxUser1.getBean(VerificationValueRepository.class);
        ActiveVerificationValueRepository activeVerificationValueRepository1 = ctxUser1.getBean(ActiveVerificationValueRepository.class);

        await().untilAsserted(() -> {
            List<DataLocation> newDataLocations = dataLocationRepository1.findAll();
            assertThat(newDataLocations).hasSize(5);

            for (DataLocation location : newDataLocations) {
                DataLocation oldLocation = oldLocations.stream().filter(l -> l.getId().equals(location.getId())).findFirst().get();
                assertThat(location.getVerified()).as("new verification date %s of location should be after the old verification date %s (that was set in the past)", location.getVerified(), oldLocation.getVerified()).isAfter(oldLocation.getVerified());
                assertThat(location.getVerified()).isBetween(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()), LocalDateTime.now(ZoneOffset.UTC));

                Optional<ActiveVerificationValue> activeVerificationValue = activeVerificationValueRepository1.findByBlockMetaDataId(location.getBlockMetaData().getId());
                assertThat(activeVerificationValue).isPresent();
                assertThat(activeVerificationValue.get().getId()).isNotNull();
                assertThat(activeVerificationValue.get().getBlockMetaData().getId()).isEqualTo(location.getBlockMetaData().getId());
                assertThat(activeVerificationValue.get().getActiveUntil()).isBetween(LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC).plus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()));

                assertThat(verificationValueRepository1.findByBlockMetaDataIdOrderByIdAsc(location.getBlockMetaData().getId())).hasSize(11);
            }
        });
    }

    @Test
    void testVerifyBlocksAtOtherPeers_triggerGenerationOfVerificationValuesFromLocalStorage() throws IOException {
        Path dataDirUser1 = this.getDataDir("user1");
        File testfile1 = ResourceUtils.getFile("classpath:testfile1.txt");
        Files.copy(testfile1.toPath(), dataDirUser1.resolve("testfile1.txt"));

        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(3));// 1 data-block + 1 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(3);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        DataLocationRepository dataLocationRepository1 = ctxUser1.getBean(DataLocationRepository.class);
        P2PBackupProperties p2PBackupProperties1 = ctxUser1.getBean(P2PBackupProperties.class);

        await().untilAsserted(() -> assertThat(dataLocationRepository1.count()).as("for 3 blocks (1 path-version-block, 1 data-block, 1 backup-index-block) should be a data location saved").isEqualTo(3));

        // set the verification date of all locations in the past so we have to verify them
        List<DataLocation> oldLocations = dataLocationRepository1.findAll();
        for (DataLocation location : oldLocations) {
            location.setVerified(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()));
            dataLocationRepository1.save(location);
        }

        // delete all verification-values of first block to trigger generation of new values from the stored block
        DataLocation oldLocation = oldLocations.get(0);
        VerificationValueRepository verificationValueRepository1 = ctxUser1.getBean(VerificationValueRepository.class);
        verificationValueRepository1.deleteAll(verificationValueRepository1.findByBlockMetaDataIdOrderByIdAsc(oldLocation.getBlockMetaData().getId()));
        assertThat(verificationValueRepository1.findByBlockMetaDataIdOrderByIdAsc(oldLocation.getBlockMetaData().getId())).isEmpty();

        ctxUser1.getBean(VerificationAgent.class).verify();
        ActiveVerificationValueRepository activeVerificationValueRepository1 = ctxUser1.getBean(ActiveVerificationValueRepository.class);

        await().untilAsserted(() -> {
            DataLocation location = dataLocationRepository1.findById(oldLocation.getId()).get();
            assertThat(location.getId()).isEqualTo(oldLocation.getId());
            assertThat(location.getVerified()).as("new verification date %s of location should be after the old verification date %s (that was set in the past)", location.getVerified(), oldLocation.getVerified()).isAfter(oldLocation.getVerified());
            assertThat(location.getVerified()).isBetween(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()), LocalDateTime.now(ZoneOffset.UTC));

            Optional<ActiveVerificationValue> activeVerificationValue = activeVerificationValueRepository1.findByBlockMetaDataId(location.getBlockMetaData().getId());
            assertThat(activeVerificationValue).isPresent();
            assertThat(activeVerificationValue.get().getId()).isNotNull();
            assertThat(activeVerificationValue.get().getBlockMetaData().getId()).isEqualTo(location.getBlockMetaData().getId());
            assertThat(activeVerificationValue.get().getActiveUntil()).isBetween(LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC).plus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()));

            assertThat(verificationValueRepository1.findByBlockMetaDataIdOrderByIdAsc(location.getBlockMetaData().getId())).hasSize(11);
        });
    }

    @Test
    void testVerifyBlocksAtOtherPeers_triggerGenerationOfVerificationValuesByRequestingFromOtherPeer() throws IOException {
        Path dataDirUser1 = this.getDataDir("user1");
        File testfile1 = ResourceUtils.getFile("classpath:testfile1.txt");
        Files.copy(testfile1.toPath(), dataDirUser1.resolve("testfile1.txt"));

        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(3));// 1 data-block + 1 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(3);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        DataLocationRepository dataLocationRepository1 = ctxUser1.getBean(DataLocationRepository.class);
        P2PBackupProperties p2PBackupProperties1 = ctxUser1.getBean(P2PBackupProperties.class);

        await().untilAsserted(() -> assertThat(dataLocationRepository1.count()).as("for 3 blocks (1 path-version-block, 1 data-block, 1 backup-index-block) should be a data location saved").isEqualTo(3));

        // set the verification date of all locations in the past so we have to verify them
        List<DataLocation> oldLocations = dataLocationRepository1.findAll();
        for (DataLocation location : oldLocations) {
            location.setVerified(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()));
            dataLocationRepository1.save(location);
        }

        // delete all verification-values of first block to trigger generation of new values
        DataLocation oldLocation = oldLocations.get(0);
        VerificationValueRepository verificationValueRepository1 = ctxUser1.getBean(VerificationValueRepository.class);
        verificationValueRepository1.deleteAll(verificationValueRepository1.findByBlockMetaDataIdOrderByIdAsc(oldLocation.getBlockMetaData().getId()));
        assertThat(verificationValueRepository1.findByBlockMetaDataIdOrderByIdAsc(oldLocation.getBlockMetaData().getId())).isEmpty();

        //also delete the local stored block
        Path pathToBlock = ctxUser1.getBean(LocalStorageService.class).loadFromLocalStorage(oldLocation.getBlockMetaData().getId()).get();
        Files.delete(pathToBlock);

        ctxUser1.getBean(VerificationAgent.class).verify();
        ActiveVerificationValueRepository activeVerificationValueRepository1 = ctxUser1.getBean(ActiveVerificationValueRepository.class);

        await().pollDelay(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            ctxUser1.getBean(VerificationAgent.class).verify(); // verify multiple times to await the response of the other peer

            DataLocation location = dataLocationRepository1.findById(oldLocation.getId()).get();
            assertThat(location.getId()).isEqualTo(oldLocation.getId());
            assertThat(location.getVerified()).as("new verification date %s of location should be after the old verification date %s (that was set in the past)", location.getVerified(), oldLocation.getVerified()).isAfter(oldLocation.getVerified());
            assertThat(location.getVerified()).isBetween(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()), LocalDateTime.now(ZoneOffset.UTC));

            assertThat(verificationValueRepository1.findByBlockMetaDataIdOrderByIdAsc(location.getBlockMetaData().getId())).hasSize(12);

            // the requested block should not be stored locally
            assertThat(Files.exists(pathToBlock)).isFalse();
        });
    }

    @Test
    void testDeleteUnverifiedBlocksAtOtherPeers() throws IOException {
        Path dataDirUser1 = this.getDataDir("user1");
        File testfile1 = ResourceUtils.getFile("classpath:testfile1.txt");
        Files.copy(testfile1.toPath(), dataDirUser1.resolve("testfile1.txt"));
        File testfile2 = ResourceUtils.getFile("classpath:testfile2.txt");
        Files.copy(testfile2.toPath(), dataDirUser1.resolve("testfile2.txt"));

        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(5));// 2 data-block + 2 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(5);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        DataLocationRepository dataLocationRepository1 = ctxUser1.getBean(DataLocationRepository.class);
        P2PBackupProperties p2PBackupProperties1 = ctxUser1.getBean(P2PBackupProperties.class);

        await().untilAsserted(() -> assertThat(dataLocationRepository1.count()).as("for 5 blocks (2 path-version-blocks, 2 data-blocks, 1 backup-index-block) should be a data location saved").isEqualTo(5));

        // set the verification-date of all data-locations to a timestamp where we have to delete them
        List<DataLocation> locations = dataLocationRepository1.findAll();
        for (DataLocation location : locations) {
            location.setVerified(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBeforeDeletion()));
            dataLocationRepository1.save(location);
        }

        ctxUser1.getBean(VerificationAgent.class).verify();
        ActiveVerificationValueRepository activeVerificationValueRepository1 = ctxUser1.getBean(ActiveVerificationValueRepository.class);

        await().untilAsserted(() -> {
            assertThat(dataLocationRepository1.count()).as("all data-locations should be deleted because they werent verified for too long").isZero();
            assertThat(activeVerificationValueRepository1.count()).as("there should be no active-verification-values because the data-locations verification-date was to old to try a new verification").isZero();

            BlockMetaDataRepository blockMetaDataRepository1 = ctxUser1.getBean(BlockMetaDataRepository.class);
            List<BlockMetaData> bmds = blockMetaDataRepository1.findAll();
            assertThat(bmds).hasSize(5);

            LocalStorageService localStorageService2 = ctxUser2.getBean(LocalStorageService.class);

            // all blocks should be deleted from user2
            for (BlockMetaData bmd : bmds) {
                assertThat(localStorageService2.loadFromLocalStorage("user1", bmd.getId())).isNotPresent();
            }
        });
    }

    @Test
    void testVerifyBlock_blockIsMissingOnOtherPeer() throws IOException {
        Path dataDirUser1 = this.getDataDir("user1");
        File testfile1 = ResourceUtils.getFile("classpath:testfile1.txt");
        Files.copy(testfile1.toPath(), dataDirUser1.resolve("testfile1.txt"));

        ctxUser1.getBean(BackupAgent.class).backup();
        await().untilAsserted(() -> assertThat(ctxUser1.getBean(BlockMetaDataRepository.class).count()).isEqualTo(3));// 1 data-block + 1 version-block + 1 backup-index-block
        await().untilAsserted(() -> {
            ctxUser1.getBean(CloudUploadAgent.class).upload();
            assertThat(ctxUser1.getBean(CloudUploadRepository.class).findAllByShareUrlIsNotNull(Pageable.unpaged())).hasSize(3);
        });
        ctxUser1.getBean(DistributionAgent.class).distribute();

        DataLocationRepository dataLocationRepository1 = ctxUser1.getBean(DataLocationRepository.class);
        P2PBackupProperties p2PBackupProperties1 = ctxUser1.getBean(P2PBackupProperties.class);

        await().untilAsserted(() -> assertThat(dataLocationRepository1.count()).as("for 3 blocks (1 path-version-block, 1 data-block, 1 backup-index-block) should be a data location saved").isEqualTo(3));

        List<DataLocation> locations = dataLocationRepository1.findAll();

        // delete the block from the first data-location-entry from user 2 from the file-system
        LocalStorageService localStorageService2 = ctxUser2.getBean(LocalStorageService.class);
        Path pathToSavedBlock = localStorageService2.loadFromLocalStorage("user1", locations.get(0).getBlockMetaData().getId()).get();
        Files.delete(pathToSavedBlock);

        // reset data-locations verify-date to trigger verification
        for (DataLocation location : locations) {
            location.setVerified(LocalDateTime.now(ZoneOffset.UTC).minus(p2PBackupProperties1.getVerificationProperties().getDurationBetweenVerifications()));
            dataLocationRepository1.save(location);
        }

        ctxUser1.getBean(VerificationAgent.class).verify();

        await().untilAsserted(() -> {
            // check if the data-location-entry of the missing block of user2 was deleted at user1
            assertThat(dataLocationRepository1.count()).isEqualTo(2);
            assertThat(dataLocationRepository1.findById(locations.get(0).getId())).isNotPresent();
            assertThat(localStorageService2.loadFromLocalStorage("user1", locations.get(0).getBlockMetaData().getId())).isNotPresent();

            // the data for the available blocks should be there
            assertThat(dataLocationRepository1.findById(locations.get(1).getId())).isPresent();
            assertThat(localStorageService2.loadFromLocalStorage("user1", locations.get(1).getBlockMetaData().getId())).isPresent();
            assertThat(dataLocationRepository1.findById(locations.get(2).getId())).isPresent();
            assertThat(localStorageService2.loadFromLocalStorage("user1", locations.get(2).getBlockMetaData().getId())).isPresent();
        });
    }
}
