package at.lucny.p2pbackup.restore.service.worker;

import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.PathVersion;
import at.lucny.p2pbackup.localstorage.service.RestorationStorageService;
import at.lucny.p2pbackup.restore.domain.RestoreBlockData;
import at.lucny.p2pbackup.restore.domain.RestorePath;
import at.lucny.p2pbackup.restore.domain.RestoreType;
import at.lucny.p2pbackup.restore.repository.RestoreBlockDataRepository;
import at.lucny.p2pbackup.restore.repository.RestorePathRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Validated
public class RestoreTaskWorker {

    private final RestoreBlockDataRepository restoreBlockDataRepository;

    private final RestorePathRepository restorePathRepository;

    private final RestorationStorageService restorationStorageService;

    public RestoreTaskWorker(RestoreBlockDataRepository restoreBlockDataRepository, RestorePathRepository restorePathRepository, RestorationStorageService restorationStorageService) {
        this.restoreBlockDataRepository = restoreBlockDataRepository;
        this.restorePathRepository = restorePathRepository;
        this.restorationStorageService = restorationStorageService;
    }

    /**
     * Creates or updates the RestoreBlockData-entry for the given block.
     * If the block is already available in the restoration-storage, do nothing.
     *
     * @param bmd
     * @param restoreType
     * @return
     */
    @Transactional
    public Optional<RestoreBlockData> createOrUpdateRestoreBlockData(BlockMetaData bmd, RestoreType restoreType) {
        if (restorationStorageService.loadFromLocalStorage(bmd.getId()).isPresent()) {
            return Optional.empty();
        }

        Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findByBlockMetaDataId(bmd.getId());
        if (restoreBlockDataOptional.isEmpty()) {
            return Optional.of(this.restoreBlockDataRepository.save(new RestoreBlockData(bmd, restoreType)));
        } else if (restoreType.ordinal() > restoreBlockDataOptional.get().getType().ordinal()) { // only "upgrade" the restore type
            restoreBlockDataOptional.get().setType(restoreType);
        }
        return restoreBlockDataOptional;
    }

    @Transactional
    public void updateRestoreTasksForFile(String blockId, PathVersion version, Path filePath) {
        Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findByBlockMetaDataId(blockId);
        RestoreType restoreType = RestoreType.RESTORE;

        if (restoreBlockDataOptional.isPresent()) {
            restoreType = restoreBlockDataOptional.get().getType();
        }

        // we only need to request data for not deleted files
        if (Boolean.FALSE.equals(version.getDeleted())) {
            Set<RestoreBlockData> restoreBlockDataSet = new HashSet<>();
            for (BlockMetaData bmd : version.getBlocks()) {
                // restore blocks with the same restore-type -> if the metadata was set to restore data then the block data must also be restored
                Optional<RestoreBlockData> restoreForBmd = this.createOrUpdateRestoreBlockData(bmd, restoreType);
                restoreForBmd.ifPresent(restoreBlockDataSet::add);
            }

            // add an entry to restore the file
            if (restoreBlockDataOptional.isPresent() && restoreType == RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA) {
                RestorePath restorePath = new RestorePath(version, filePath.toString());
                restorePath.getMissingBlocks().addAll(restoreBlockDataSet);
                this.restorePathRepository.save(restorePath);
            }
        }
    }

    @Transactional
    public void addRestoreTasksForMissingBlocks(RestorePath restorePath, List<BlockMetaData> missingBlocks) {
        for (BlockMetaData missingBlock : missingBlocks) {
            restorePath.getMissingBlocks().add(this.restoreBlockDataRepository.save(new RestoreBlockData(missingBlock, RestoreType.RESTORE)));
        }
        this.restorePathRepository.save(restorePath);
    }
}
