package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.configuration.support.RecoveryState;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.DataLocation;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.PathVersionRepository;
import at.lucny.p2pbackup.core.support.FileUtils;
import at.lucny.p2pbackup.localstorage.service.RestorationStorageService;
import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.dto.RestoreBlock;
import at.lucny.p2pbackup.network.dto.RestoreBlockFor;
import at.lucny.p2pbackup.network.service.ClientService;
import at.lucny.p2pbackup.network.service.NettyClient;
import at.lucny.p2pbackup.restore.domain.RestoreBlockData;
import at.lucny.p2pbackup.restore.domain.RestorePath;
import at.lucny.p2pbackup.restore.domain.RestoreType;
import at.lucny.p2pbackup.restore.repository.RestoreBlockDataRepository;
import at.lucny.p2pbackup.restore.repository.RestorePathRepository;
import at.lucny.p2pbackup.user.domain.User;
import com.google.common.collect.Lists;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Validated
@Service
public class RestorationServiceImpl implements RestorationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestorationServiceImpl.class);

    private final PathVersionRepository pathVersionRepository;

    private final RestorePathRepository restorePathRepository;

    private final RestoreBlockDataRepository restoreBlockDataRepository;

    private final RestorationStorageService restorationStorageService;

    private final BlockMetaDataRepository blockMetaDataRepository;

    private final ClientService clientService;

    private final PlatformTransactionManager txManager;

    private final Configuration configuration;

    private final FileUtils fileUtils = new FileUtils();

    public RestorationServiceImpl(PathVersionRepository pathVersionRepository, RestorePathRepository restorePathRepository, RestoreBlockDataRepository restoreBlockDataRepository, RestorationStorageService restorationStorageService, BlockMetaDataRepository blockMetaDataRepository, @Lazy ClientService clientService, PlatformTransactionManager txManager, Configuration configuration) {
        this.pathVersionRepository = pathVersionRepository;
        this.restorePathRepository = restorePathRepository;
        this.restoreBlockDataRepository = restoreBlockDataRepository;
        this.restorationStorageService = restorationStorageService;
        this.blockMetaDataRepository = blockMetaDataRepository;
        this.clientService = clientService;
        this.txManager = txManager;
        this.configuration = configuration;
    }

    @Override
    public void restoreBlocks() {
        LOGGER.trace("begin restoreBlocks()");

        List<RestoreType> types = Arrays.asList(RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA, RestoreType.RESTORE, RestoreType.RECOVER);

        RecoveryState recoveryState = this.configuration.get(RecoveryState.class, ConfigurationConstants.PROPERTY_RECOVERY_STATE, null);
        LOGGER.debug("recovery-state is {}", recoveryState);

        long pathsToRestore = this.restorePathRepository.count();
        long blocksToRestore = this.restoreBlockDataRepository.count();

        // if we are in recovery mode
        if (recoveryState != null) {
            LOGGER.info("start to recover blocks and files");
            switch (recoveryState) {
                case INITIALIZED -> {
                    LOGGER.info("waiting for recovery-start");
                    return;
                }
                case RECOVER_DATA -> {
                    // if there are blocks with priority to recover fetch them first -> this means the latest data-versions are not recovered
                    if (this.restoreBlockDataRepository.countByTypeIn(Arrays.asList(RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA, RestoreType.RESTORE)) > 0) {
                        types = Arrays.asList(RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA, RestoreType.RESTORE);
                    } else {
                        // otherwise switch to recovering the remaining metadata
                        this.configuration.setProperty(ConfigurationConstants.PROPERTY_RECOVERY_STATE, RecoveryState.RECOVER_METADATA);
                    }
                }
                case RECOVER_METADATA -> {
                    // if there are no blocks to recover stop recovery-mode
                    if (blocksToRestore == 0) {
                        LOGGER.info("all blocks recovered, stop recovery-mode");
                        this.configuration.clearProperty(ConfigurationConstants.PROPERTY_RECOVERY_STATE);
                    }
                }
            }
        } else { // if we are not in recovery mode the restore is done if no more restore-path-entries exist
            if (pathsToRestore == 0 && blocksToRestore == 0) {
                LOGGER.debug("no more blocks to restore");
                this.restorationStorageService.deleteAll();
            } else {
                LOGGER.info("restoring {} paths and {} blocks", pathsToRestore, blocksToRestore);
            }
        }

        this.restoreFilesWithoutMissingBlocks();

        this.requestBlocks(types);

        LOGGER.trace("end restoreBlocks");
    }

    /**
     * Restores all files without missing blocks.
     */
    private void restoreFilesWithoutMissingBlocks() {
        long totalNrOfRestorableFiles = this.restorePathRepository.countWithoutMissingBlocks();
        if (totalNrOfRestorableFiles == 0) {
            return;
        }

        LOGGER.info("trying to restore {} files", totalNrOfRestorableFiles);

        Page<RestorePath> restorePathsWithoutMissingBlocks = null;
        do {
            restorePathsWithoutMissingBlocks = this.restorePathRepository.findWithoutMissingBlocks(PageRequest.of(0, 100, Sort.Direction.ASC, "id"));

            for (RestorePath restorePath : restorePathsWithoutMissingBlocks.getContent()) {
                if (this.restoreFile(restorePath)) {
                    LOGGER.info("restored file {}", restorePath.getPath());
                    this.restorePathRepository.delete(restorePath);
                }
            }
        } while (restorePathsWithoutMissingBlocks.hasNext());

        LOGGER.info("restored all currently available files");
    }

    /**
     * Restores a file represented by the {@link RestorePath}. Checks if all blocks are available. If not the restoration is skipped and the blocks will be added as {@link RestoreBlockData}-tasks.
     *
     * @param restorePath the file to restore
     * @return true if the file could be restored, otherwise false
     */
    private boolean restoreFile(RestorePath restorePath) {
        List<BlockMetaData> blockMetaDatas = this.pathVersionRepository.findByIdFetchBlockMetaData(restorePath.getPathVersion().getId()).getBlocks();
        List<Path> blockPaths = new ArrayList<>();
        List<BlockMetaData> missingBlocks = new ArrayList<>();

        for (BlockMetaData blockMetaData : blockMetaDatas) {
            Optional<Path> pathToBlockOptional = this.restorationStorageService.loadFromLocalStorage(blockMetaData.getId());

            if (pathToBlockOptional.isPresent()) {
                blockPaths.add(pathToBlockOptional.get());
            } else {
                // if the path is missing request the block again (this is an error case)
                missingBlocks.add(blockMetaData);
            }
        }

        // if blocks are missing in the local store skip the restore for this file - the fetching of data should be done automatically afterwards
        if (!missingBlocks.isEmpty()) {
            new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
                for (BlockMetaData missingBlock : missingBlocks) {
                    restorePath.getMissingBlocks().add(this.restoreBlockDataRepository.save(new RestoreBlockData(missingBlock, RestoreType.RESTORE)));
                }
                this.restorePathRepository.save(restorePath);
            });
            return false;
        } else {
            return this.restoreFileFromBlocks(restorePath, blockPaths);
        }
    }

    /**
     * Restores the file represented by the given {@link RestorePath} from the given blocks.
     *
     * @param restorePath the {@link RestorePath} representing the file to be restored
     * @param blockPaths  the blocks the file is contained of
     * @return true if the file could be restored, otherwise false
     */
    private boolean restoreFileFromBlocks(RestorePath restorePath, List<Path> blockPaths) {
        Path destinationFile = Paths.get(restorePath.getPath());
        try {
            Files.deleteIfExists(destinationFile); // delete the file if it already exists to recreate it
            Files.createDirectories(destinationFile.getParent()); // create all parent directories
            Files.createFile(destinationFile); // create the empty file
            try (FileChannel destinationChannel = FileChannel.open(destinationFile, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                for (Path path : blockPaths) {
                    try (FileChannel readChannel = FileChannel.open(path, StandardOpenOption.READ)) {
                        readChannel.transferTo(0, Long.MAX_VALUE, destinationChannel);
                    }
                }
            }
        } catch (IOException e) {
            this.fileUtils.deleteIfExistsSilent(destinationFile);
            LOGGER.error("unable to delete already restored file {}", destinationFile, e);
            return false;
        }
        return true;
    }

    private void requestBlocks(List<RestoreType> types) {
        List<String> onlineUsers = this.clientService.getOnlineClients().stream().map(NettyClient::getUser).map(User::getId).toList();
        if (CollectionUtils.isEmpty(onlineUsers)) {
            return;
        }

        long nrOfRequestedBlocks = 0;
        List<String> restoreBlockIds = this.restoreBlockDataRepository.findIdsByUserIdsAndTypes(onlineUsers, types, PageRequest.of(0, 1000)).getContent();
        List<List<String>> partitionedRestoreBlockIds = Lists.partition(restoreBlockIds, 100);
        for (List<String> blockIdsToRestore : partitionedRestoreBlockIds) {
            List<BlockMetaData> blocksToRestore = this.blockMetaDataRepository.findByIdsFetchLocations(blockIdsToRestore);
            for (BlockMetaData block : blocksToRestore) {
                this.restoreBlock(block);

                nrOfRequestedBlocks++;
                if (nrOfRequestedBlocks % 100 == 0) {
                    LOGGER.info("requested {} blocks for restore", nrOfRequestedBlocks);
                }
            }
        }

        if (nrOfRequestedBlocks > 0) {
            LOGGER.info("requested {} blocks total for restore", nrOfRequestedBlocks);
        }
    }

    private void restoreBlock(BlockMetaData block) {
        String blockId = block.getId();

        if (this.restorationStorageService.loadFromLocalStorage(blockId).isPresent()) {
            new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
                // cleanup restoreBlockData if needed
                Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findByBlockMetaDataId(block.getId());
                if (restoreBlockDataOptional.isPresent()) {
                    List<RestorePath> restorePaths = this.restorePathRepository.findByRestoreBlockData(restoreBlockDataOptional.get());
                    for (RestorePath path : restorePaths) {
                        path.getMissingBlocks().removeIf(b -> b.getBlockMetaData().getId().equals(block.getId()));
                    }
                    this.restoreBlockDataRepository.delete(restoreBlockDataOptional.get());
                }
            });
        } else {
            Optional<NettyClient> clientOptional = this.getUserForRestore(block);
            if (clientOptional.isPresent()) {
                var request = RestoreBlock.newBuilder().addId(block.getId()).setFor(RestoreBlockFor.RESTORE);
                ProtocolMessage message = ProtocolMessage.newBuilder().setRestoreBlock(request).build();
                try {
                    clientOptional.get().write(message);
                } catch (RuntimeException e) {
                    LOGGER.warn("unable to send restore-block for block {} to {}", block.getId(), clientOptional.get().getUser().getId());
                }
            } else {
                LOGGER.debug("no client to request block {} online", block.getId());
            }
        }

    }

    private Optional<NettyClient> getUserForRestore(BlockMetaData block) {
        List<NettyClient> onlineUser = this.clientService.getOnlineClients();
        if (onlineUser.isEmpty()) {
            return Optional.empty();
        }

        List<String> userIds = block.getLocations().stream().map(DataLocation::getUserId).toList();
        List<NettyClient> usersToRestoreFrom = new ArrayList<>(onlineUser.stream()
                .filter(client -> userIds.contains(client.getUser().getId())).toList()); // wrap in new list so we can shuffle ist

        if (usersToRestoreFrom.isEmpty()) {
            return Optional.empty();
        }
        Collections.shuffle(usersToRestoreFrom);

        // return needed amount of clients for replication
        return Optional.of(usersToRestoreFrom.get(0));
    }

    @Override
    @Transactional
    public void saveBlock(String blockId, ByteBuffer data) {
        LOGGER.trace("begin saveBlock(blockId={}, data={} remaining", blockId, data.remaining());

        // cleanup restoreBlockData if needed
        BlockMetaData bmd = this.blockMetaDataRepository.getById(blockId);
        Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findByBlockMetaDataId(blockId);
        if (restoreBlockDataOptional.isPresent()) {
            RestoreBlockData restoreBlockData = restoreBlockDataOptional.get();
            LOGGER.debug("block {} has restoration-type {}", blockId, restoreBlockData.getType());
            if (restoreBlockData.getType() == RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA || restoreBlockData.getType() == RestoreType.RESTORE) {
                LOGGER.debug("save block {} in restoration-storage", blockId);
                this.restorationStorageService.saveInLocalStorage(blockId, data.duplicate());
                List<RestorePath> restorePaths = this.restorePathRepository.findByRestoreBlockData(restoreBlockData);
                for (RestorePath path : restorePaths) {
                    path.getMissingBlocks().removeIf(b -> b.getBlockMetaData().getId().equals(bmd.getId()));
                }
            }
            this.restoreBlockDataRepository.delete(restoreBlockData);
        }

        LOGGER.trace("end saveBlock(blockId={}, data={} remaining", blockId, data.remaining());
    }
}
