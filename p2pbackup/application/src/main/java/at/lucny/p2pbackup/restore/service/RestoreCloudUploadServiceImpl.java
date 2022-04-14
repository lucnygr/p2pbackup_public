package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.backup.support.BackupConstants;
import at.lucny.p2pbackup.cloud.CloudStorageService;
import at.lucny.p2pbackup.cloud.CloudStorageServiceProvider;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.CloudUpload;
import at.lucny.p2pbackup.core.service.BlockEncryptionService;
import at.lucny.p2pbackup.localstorage.dto.LocalStorageEntry;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.restore.service.worker.RecoverMetadataWorker;
import at.lucny.p2pbackup.upload.service.CloudUploadService;
import at.lucny.p2pbackup.verification.service.VerificationValueService;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Validated
public class RestoreCloudUploadServiceImpl implements RestoreCloudUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreCloudUploadServiceImpl.class);

    private final CloudStorageServiceProvider cloudStorageServiceProvider;

    private final LocalStorageService localStorageService;

    private final BlockEncryptionService blockEncryptionService;

    private final CloudUploadService cloudUploadService;

    private final RecoverMetadataWorker recoverMetadataWorker;

    private final VerificationValueService verificationValueService;

    private final RecoveryService recoveryService;

    public RestoreCloudUploadServiceImpl(CloudStorageServiceProvider cloudStorageServiceProvider, LocalStorageService localStorageService, BlockEncryptionService blockEncryptionService, CloudUploadService cloudUploadService, RecoverMetadataWorker recoverMetadataWorker, VerificationValueService verificationValueService, RecoveryService recoveryService) {
        this.cloudStorageServiceProvider = cloudStorageServiceProvider;
        this.localStorageService = localStorageService;
        this.blockEncryptionService = blockEncryptionService;
        this.cloudUploadService = cloudUploadService;
        this.recoverMetadataWorker = recoverMetadataWorker;
        this.verificationValueService = verificationValueService;
        this.recoveryService = recoveryService;
    }

    @Override
    @Async
    public CompletableFuture<Void> recoverFromCloudStorages() {
        LOGGER.trace("begin recoverFromCloudStorages()");

        this.recoveryService.initializeRecoveryMode();

        for (CloudStorageService cloudStorageService : this.cloudStorageServiceProvider.getInitializedCloudStorageServices()) {
            List<String> fileList = cloudStorageService.list();
            LOGGER.info("recovering {} blocks from cloud-storage {}", fileList.size(), cloudStorageService.getId());
            for (int i = 0; i < fileList.size(); i++) {
                this.recoverFromCloudStorage(cloudStorageService, fileList.get(i));
                if (i % 10 == 0) {
                    LOGGER.info("recovered {} blocks from cloud-storage {}", i, cloudStorageService.getId());
                }
            }
            LOGGER.info("recovered {} blocks total from cloud-storage {}", fileList.size(), cloudStorageService.getId());
        }

        LOGGER.trace("end recoverFromCloudStorages()");
        return CompletableFuture.completedFuture(null);
    }

    private void recoverFromCloudStorage(CloudStorageService cloudStorageService, String blockId) {
        LOGGER.trace("begin recoverFromCloudStorage(cloudStorageService={}, blockId={})", cloudStorageService, blockId);
        BlockMetaData bmd = this.recoverMetadataWorker.createOrUpdateBlockMetaData(blockId, null);

        Optional<CloudUpload> cloudUploadOptional = this.cloudUploadService.getCloudUpload(blockId);
        if (cloudUploadOptional.isPresent()) {
            LOGGER.debug("block {} already processed", blockId);
            return;
        }
        String url = cloudStorageService.share(blockId);
        LOGGER.debug("downloading {} from url {}", blockId, url);

        try (InputStream is = new URL(url).openStream()) {
            byte[] data = ByteStreams.toByteArray(is);
            ByteBuffer dataInByteBuffer = ByteBuffer.wrap(data);
            LOGGER.debug("downloaded {} from url {}", blockId, url);

            this.blockEncryptionService.decrypt(dataInByteBuffer, blockId.getBytes(StandardCharsets.UTF_8), plainData -> {
                if (bmd.getId().startsWith(BackupConstants.BACKUP_INDEX_BLOCK_PREFIX)) {
                    this.recoveryService.recoverBackupIndex(null, plainData.duplicate());
                }
            });

            LOGGER.trace("generate verificationValues for block {}", blockId);
            this.verificationValueService.ensureVerificationValues(bmd.getId(), dataInByteBuffer);

            // save in local storage to potentially redistribute them
            LocalStorageEntry localStorageEntry = this.localStorageService.saveInLocalStorage(bmd.getId(), dataInByteBuffer);
            CloudUpload upload = this.cloudUploadService.saveCloudUpload(bmd.getId(), localStorageEntry.macSecret(), localStorageEntry.mac());
            this.cloudUploadService.updateCloudUpload(upload.getId(), cloudStorageService.getId(), url);
        } catch (IOException e) {
            LOGGER.warn("unable to restore block {} from cloudprovider {} via url {}", blockId, cloudStorageService.getId(), url, e);
        }
        LOGGER.trace("end recoverFromCloudStorage(cloudStorageService={}, blockId={})", cloudStorageService, blockId);
    }
}
