package at.lucny.p2pbackup.upload.service;

import at.lucny.p2pbackup.application.support.StopApplicationEvent;
import at.lucny.p2pbackup.backup.support.BackupUtils;
import at.lucny.p2pbackup.cloud.CloudStorageService;
import at.lucny.p2pbackup.cloud.CloudStorageServiceProvider;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.CloudUpload;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.CloudUploadRepository;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Validated
public class CloudUploadServiceImpl implements CloudUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudUploadServiceImpl.class);

    private final LocalStorageService localStorageService;

    private final CloudUploadRepository cloudUploadRepository;

    private final CloudStorageServiceProvider cloudStorageServiceProvider;

    private final BlockMetaDataRepository blockMetaDataRepository;

    private boolean shutdown;

    public CloudUploadServiceImpl(LocalStorageService localStorageService, CloudUploadRepository cloudUploadRepository, CloudStorageServiceProvider cloudStorageServiceProvider, BlockMetaDataRepository blockMetaDataRepository) {
        this.localStorageService = localStorageService;
        this.cloudUploadRepository = cloudUploadRepository;
        this.cloudStorageServiceProvider = cloudStorageServiceProvider;
        this.blockMetaDataRepository = blockMetaDataRepository;
    }

    @EventListener
    public void onApplicationEvent(StopApplicationEvent event) {
        this.shutdown();
    }

    @PreDestroy
    public synchronized void shutdown() {
        LOGGER.trace("begin shutdown()");

        this.shutdown = true;

        LOGGER.trace("end shutdown()");
    }

    @Override
    @Transactional
    public CloudUpload saveCloudUpload(String blockMetaDataId, String macSecret, String mac) {
        BlockMetaData bmd = this.blockMetaDataRepository.getById(blockMetaDataId);
        return this.saveCloudUpload(bmd, macSecret, mac);
    }

    @Override
    @Transactional
    public CloudUpload saveCloudUpload(BlockMetaData bmd, String macSecret, String mac) {
        Optional<CloudUpload> upload = this.cloudUploadRepository.findByBlockMetaDataId(bmd.getId());
        if (upload.isPresent() && !upload.get().getEncryptedBlockMac().equals(mac)) {
            LOGGER.info("removing existing cloud upload {} for reupload", upload.get().getId());
            this.removeFromCloudStorageService(upload.get());
            upload.get().setMacSecret(macSecret);
            upload.get().setEncryptedBlockMac(mac);
            return upload.get();
        } else {
            CloudUpload cloudUpload = new CloudUpload(bmd, macSecret, mac);
            return this.cloudUploadRepository.save(cloudUpload);
        }
    }

    @SneakyThrows
    @Override
    public void uploadLocalBackupBlocks() {
        LOGGER.trace("begin uploadLocalBackupBlocks()");

        LOGGER.debug("uploading available blocks in local-storage to cloud-storage");
        long totalNrOfCloudUploads = this.cloudUploadRepository.countByShareUrlIsNull();
        if (totalNrOfCloudUploads == 0) {
            return;
        }

        if (CollectionUtils.isEmpty(this.cloudStorageServiceProvider.getInitializedCloudStorageServices())) {
            LOGGER.warn("no cloud storage service configured");
            return;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try {

            LOGGER.info("prepare to upload up to {} blocks", totalNrOfCloudUploads);

            // load 100 cloud-uploads per iteration and upload them. they are no longer found by the sql-query, so start again with 10 uploads from the beginning
            Pageable pageRequest = PageRequest.of(0, 10, Sort.Direction.ASC, "id");
            Page<CloudUpload> backupBlocks;

            long nrOfProcessedUploads = 0;

            do {
                backupBlocks = this.cloudUploadRepository.findAllByShareUrlIsNull(pageRequest);
                List<CloudUpload> uploadsToDelete = new ArrayList<>();
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (CloudUpload localBackupBlock : backupBlocks.getContent()) {
                    Optional<Path> pathToBlock = this.localStorageService.loadFromLocalStorage(localBackupBlock.getBlockMetaData().getId());
                    if (pathToBlock.isPresent()) {
                        Path blockPath = pathToBlock.get();

                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            LOGGER.trace("beginning to upload block {}", blockPath.getFileName());
                            CloudStorageService cloudStorageService = this.pickRandomCloudStorageService().orElseThrow(() -> new IllegalStateException("no cloud storage provider configured"));
                            cloudStorageService.upload(blockPath);
                            String publicUrl = cloudStorageService.share(blockPath.getFileName().toString());
                            LOGGER.debug("share url for data {} is {}", blockPath.getFileName(), publicUrl);

                            localBackupBlock.setProviderId(cloudStorageService.getId());
                            localBackupBlock.setShareUrl(publicUrl);
                            this.cloudUploadRepository.save(localBackupBlock);
                        }, executorService);
                        futures.add(future);
                    } else {
                        LOGGER.warn("block {} should be distributed but is not available, request from other peers via distribution service", localBackupBlock.getBlockMetaData().getId());
                        uploadsToDelete.add(localBackupBlock);
                    }

                    nrOfProcessedUploads++;
                }

                CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));

                if (!CollectionUtils.isEmpty(uploadsToDelete)) {
                    this.cloudUploadRepository.flush();
                    this.cloudUploadRepository.deleteAllInBatch(uploadsToDelete);
                }

                allFutures.join();
                LOGGER.info("processed {}/{} cloud-upload-entries", nrOfProcessedUploads, backupBlocks.getTotalElements());
            } while (backupBlocks.hasNext() && !this.shutdown);

            if (nrOfProcessedUploads > 0) {
                LOGGER.info("processed {} cloud-upload-entries total", nrOfProcessedUploads);
            }
        } finally {
            executorService.shutdown();
            if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }
        LOGGER.trace("end uploadLocalBackupBlocks");
    }

    private Optional<CloudStorageService> pickRandomCloudStorageService() {
        List<CloudStorageService> list = this.cloudStorageServiceProvider.getInitializedCloudStorageServices();
        if (CollectionUtils.isEmpty(list)) {
            return Optional.empty();
        }
        int index = BackupUtils.RANDOM.nextInt(list.size());
        return Optional.of(list.get(index));
    }

    @Override
    @Transactional
    public void removeCloudUploadByBlockMetaDataId(String bmdId) {
        this.cloudUploadRepository.flush();
        Optional<CloudUpload> cloudUpload = this.cloudUploadRepository.findByBlockMetaDataId(bmdId);
        cloudUpload.ifPresent(this::removeCloudUploadInternal);
    }

    @Override
    @Transactional
    public void removeCloudUpload(CloudUpload cloudUpload) {
        Optional<CloudUpload> cloudUploadOptional = this.cloudUploadRepository.findById(cloudUpload.getId());
        cloudUploadOptional.ifPresent(this::removeCloudUploadInternal);
    }

    private void removeCloudUploadInternal(CloudUpload cloudUpload) {
        boolean removedFromLocalStorage = this.localStorageService.removeFromLocalStorage(cloudUpload.getBlockMetaData().getId());
        if (removedFromLocalStorage) {
            CloudStorageService service = this.cloudStorageServiceProvider.getInitializedCloudStorageService(cloudUpload.getProviderId()).orElseThrow(() -> new IllegalStateException("Unknown cloud-storage-provider " + cloudUpload.getProviderId()));
            service.delete(cloudUpload.getBlockMetaData().getId());

            this.cloudUploadRepository.flush();
            this.cloudUploadRepository.deleteAllInBatch(Collections.singletonList(cloudUpload));
        }
    }

    @Override
    @Transactional
    public void removeFromCloudStorageService(String bmdId) {
        Optional<CloudUpload> optLocalBackupBlock = this.cloudUploadRepository.findByBlockMetaDataId(bmdId);
        if (optLocalBackupBlock.isPresent()) {
            this.removeFromCloudStorageService(optLocalBackupBlock.get());
        } else {
            LOGGER.debug("block {} is no longer in local storage", bmdId);
        }
    }

    private void removeFromCloudStorageService(CloudUpload cloudUpload) {
        if (cloudUpload.getShareUrl() != null) {
            CloudStorageService service = this.cloudStorageServiceProvider.getInitializedCloudStorageService(cloudUpload.getProviderId()).orElseThrow(() -> new IllegalStateException("Unknown cloud-storage-provider " + cloudUpload.getProviderId()));
            service.delete(cloudUpload.getBlockMetaData().getId());

            cloudUpload.setProviderId(null);
            cloudUpload.setShareUrl(null);
            LOGGER.debug("removed block {} from cloud", cloudUpload.getBlockMetaData().getId());
        } else {
            LOGGER.debug("block {} is no longer in cloud storage", cloudUpload.getBlockMetaData().getId());
        }
    }
}
