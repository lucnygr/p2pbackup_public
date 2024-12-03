package at.lucny.p2pbackup.restore.service.worker;

import at.lucny.p2pbackup.core.service.BlockEncryptionService;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.localstorage.service.RestorationStorageService;
import at.lucny.p2pbackup.restore.domain.RestoreBlockData;
import at.lucny.p2pbackup.restore.domain.RestorePath;
import at.lucny.p2pbackup.restore.domain.RestoreType;
import at.lucny.p2pbackup.restore.repository.RestoreBlockDataRepository;
import at.lucny.p2pbackup.restore.repository.RestorePathRepository;
import at.lucny.p2pbackup.restore.service.RecoveryServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Validated
public class RestoreBlockWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreBlockWorker.class);

    private final BlockEncryptionService blockEncryptionService;

    private final RecoveryServiceImpl recoveryService;

    private final RestoreBlockDataRepository restoreBlockDataRepository;

    private final RestorationStorageService restorationStorageService;

    private final LocalStorageService localStorageService;

    private final RestorePathRepository restorePathRepository;

    public RestoreBlockWorker(BlockEncryptionService blockEncryptionService, RecoveryServiceImpl recoveryService, RestoreBlockDataRepository restoreBlockDataRepository, RestorationStorageService restorationStorageService, LocalStorageService localStorageService, RestorePathRepository restorePathRepository) {
        this.blockEncryptionService = blockEncryptionService;
        this.recoveryService = recoveryService;
        this.restoreBlockDataRepository = restoreBlockDataRepository;
        this.restorationStorageService = restorationStorageService;
        this.localStorageService = localStorageService;
        this.restorePathRepository = restorePathRepository;
    }

    @Transactional
    public boolean restoreBlockFromLocalStorage(String blockId) {
        Optional<Path> optionalBlockInLocalStorage = this.localStorageService.loadFromLocalStorage(blockId);
        if (optionalBlockInLocalStorage.isPresent()) {
            Path path = optionalBlockInLocalStorage.get();
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                ByteBuffer buffer = ByteBuffer.allocate((int) path.toFile().length() * 2);
                channel.read(buffer);
                buffer.flip();
                this.restoreBlock(null, blockId, buffer.duplicate());
                return true;
            } catch (IOException ioe) {
                LOGGER.warn("unable to load block {} from local storage", blockId);
            }
        }
        return false;
    }

    @Transactional
    public void restoreBlock(String userId, String blockId, ByteBuffer encryptedData) {
        this.blockEncryptionService.decrypt(encryptedData, blockId.getBytes(StandardCharsets.UTF_8), plainDataBuffer -> {
            LOGGER.debug("restoring block {}", blockId);
            boolean isDataBlock = true;
            if (this.recoveryService.isRecoveryActive()) {
                // try to recover metadata and create restoration-tasks if its an ongoing recovery
                isDataBlock = this.recoveryService.recoverMetaData(userId, blockId, plainDataBuffer.duplicate());
            }

            if (isDataBlock) {
                this.saveBlock(blockId, plainDataBuffer.duplicate()); // save block for data restoration
            }
        });

        this.deleteRestoreBlockData(blockId);
    }

    private void saveBlock(String blockId, ByteBuffer plainData) {
        LOGGER.trace("begin saveBlock(blockId={}, plainData={} remaining", blockId, plainData.remaining());

        // there must be a restore-task to save the block
        Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findByBlockMetaDataId(blockId);
        if (restoreBlockDataOptional.isPresent()) {
            RestoreBlockData restoreBlockData = restoreBlockDataOptional.get();
            LOGGER.debug("block {} has restoration-type {}", blockId, restoreBlockData.getType());
            if (restoreBlockData.getType() == RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA || restoreBlockData.getType() == RestoreType.RESTORE) {
                LOGGER.debug("save block {} in restoration-storage", blockId);
                this.restorationStorageService.saveInLocalStorage(blockId, plainData.duplicate());
            }
        }

        LOGGER.trace("end saveBlock(blockId={}, plainData={} remaining", blockId, plainData.remaining());
    }

    @Transactional
    public void deleteRestoreBlockData(String blockId) {
        Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findByBlockMetaDataId(blockId);

        if (restoreBlockDataOptional.isPresent()) {
            RestoreBlockData restoreBlockData = restoreBlockDataOptional.get();

            // remove the block from RestorePath-tasks
            List<RestorePath> restorePaths = this.restorePathRepository.findByRestoreBlockData(restoreBlockData);
            for (RestorePath path : restorePaths) {
                path.getMissingBlocks().removeIf(b -> b.getBlockMetaData().getId().equals(blockId));
            }

            this.restoreBlockDataRepository.flush();
            this.restoreBlockDataRepository.deleteAllInBatch(Collections.singletonList(restoreBlockData));
        }
    }

}
