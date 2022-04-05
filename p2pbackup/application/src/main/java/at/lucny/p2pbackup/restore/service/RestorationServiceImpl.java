package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.configuration.support.RecoveryState;
import at.lucny.p2pbackup.core.domain.*;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.PathDataRepository;
import at.lucny.p2pbackup.core.repository.PathVersionRepository;
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
import java.time.LocalDateTime;
import java.util.*;

@Validated
@Service
public class RestorationServiceImpl implements RestorationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestorationServiceImpl.class);

    private final PathDataRepository pathDataRepository;

    private final PathVersionRepository pathVersionRepository;

    private final RestorePathRepository restorePathRepository;

    private final RestoreBlockDataRepository restoreBlockDataRepository;

    private final RestorationStorageService restorationStorageService;

    private final BlockMetaDataRepository blockMetaDataRepository;

    private final ClientService clientService;

    private final PlatformTransactionManager txManager;

    private final Configuration configuration;

    public RestorationServiceImpl(PathDataRepository pathDataRepository, PathVersionRepository pathVersionRepository, RestorePathRepository restorePathRepository, RestoreBlockDataRepository restoreBlockDataRepository, RestorationStorageService restorationStorageService, BlockMetaDataRepository blockMetaDataRepository, @Lazy ClientService clientService, PlatformTransactionManager txManager, Configuration configuration) {
        this.pathDataRepository = pathDataRepository;
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
    @Transactional
    public void beginRestore(RootDirectory rootDirectory, LocalDateTime forDate, Path targetDir) {
        LOGGER.trace("begin beginRestore(rootDirectory={}, forDate={}, targetDir={}", rootDirectory, forDate, targetDir);
        LOGGER.info("start restore for directory {}", rootDirectory.getPath());

        List<PathData> pathDataList = this.pathDataRepository.findByRootDirectoryAndPathVersionForDateExists(rootDirectory, forDate);
        for (PathData pathData : pathDataList) {
            Optional<PathVersion> relevantVersion = pathData.getVersions().stream()
                    .filter(pv -> !pv.getDate().isAfter(forDate)) // remove all versions that are after the given restore date (should already be done via sql)
                    .reduce((first, second) -> { // calculate the latest/nearest version to the given restore date
                        if (first.getDate().isBefore(second.getDate())) {
                            return second;
                        }
                        return first;
                    })
                    .filter(v -> !v.getDeleted()); // if the latest version is a deleted version we must not restore this file
            if (relevantVersion.isEmpty()) {
                continue;
            }

            PathVersion pathVersion = this.pathVersionRepository.findByIdFetchBlockMetaData(relevantVersion.get().getId());
            RestorePath restorePath = new RestorePath(pathVersion, targetDir.resolve(pathData.getPath()).toString());

            for (BlockMetaData blockMetaData : pathVersion.getBlocks()) {
                BlockMetaDataId bmdId = new BlockMetaDataId(blockMetaData);
                RestoreBlockData restoreBlockData = this.restoreBlockDataRepository.findById(bmdId).orElse(this.restoreBlockDataRepository.save(new RestoreBlockData(new BlockMetaDataId(blockMetaData), RestoreType.RESTORE)));
                restorePath.getMissingBlocks().add(restoreBlockData);
            }
            this.restorePathRepository.save(restorePath);
        }

        LOGGER.trace("end beginRestore");
    }

    @Override
    public void restoreBlocks() {
        LOGGER.info("start to restore blocks and files");

        List<RestoreType> types = Arrays.asList(RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA, RestoreType.RESTORE, RestoreType.RECOVER);

        RecoveryState recoveryState = this.configuration.get(RecoveryState.class, ConfigurationConstants.PROPERTY_RECOVERY_STATE, null);
        LOGGER.debug("recovery-state is {}", recoveryState);

        // if we are in recovery mode
        if (recoveryState != null) {
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
                    if (this.restoreBlockDataRepository.count() == 0) {
                        LOGGER.info("all blocks recovered, stop recovery-mode");
                        this.configuration.clearProperty(ConfigurationConstants.PROPERTY_RECOVERY_STATE);
                    }
                }
            }
        } else { // if we are not in recovery mode the restore is done if no more restore-path-entries exist
            if (this.restorePathRepository.count() == 0 && this.restoreBlockDataRepository.count() == 0) {
                LOGGER.debug("no more blocks to restore");
                this.restorationStorageService.deleteAll();
            }
        }

        Page<RestorePath> restorePathsWithoutMissingBlocks = null;
        do {
            restorePathsWithoutMissingBlocks = this.restorePathRepository.findWithoutMissingBlocks(PageRequest.of(0, 100, Sort.Direction.ASC, "id"));

            for (RestorePath restorePath : restorePathsWithoutMissingBlocks.getContent()) {
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
                            restorePath.getMissingBlocks().add(this.restoreBlockDataRepository.save(new RestoreBlockData(new BlockMetaDataId(missingBlock), RestoreType.RESTORE)));
                        }
                        this.restorePathRepository.save(restorePath);
                    });
                    continue;
                }

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
                        this.restorePathRepository.delete(restorePath);
                    } catch (IOException e) {
                        LOGGER.error("unable to restore file {}", destinationFile, e);
                    }
                } catch (IOException e) {
                    LOGGER.error("unable to delete already restored file {}", destinationFile, e);
                }
            }
        } while (restorePathsWithoutMissingBlocks.hasNext());

        List<String> onlineUsers = this.clientService.getOnlineClients().stream().map(NettyClient::getUser).map(User::getId).toList();
        if (!CollectionUtils.isEmpty(onlineUsers)) {
            List<String> restoreBlockIds = this.restoreBlockDataRepository.findIdsByUserIdsAndTypes(onlineUsers, types, PageRequest.of(0, 1000)).getContent();
            List<List<String>> partitionedRestoreBlockIds = Lists.partition(restoreBlockIds, 100);
            for (List<String> blockIdsToRestore : partitionedRestoreBlockIds) {
                List<BlockMetaData> blocksToRestore = this.blockMetaDataRepository.findByIdsFetchLocations(blockIdsToRestore);
                for (BlockMetaData block : blocksToRestore) {
                    String blockId = block.getId();

                    if (this.restorationStorageService.loadFromLocalStorage(blockId).isPresent()) {
                        new TransactionTemplate(this.txManager).executeWithoutResult(status -> {
                            // cleanup restoreBlockData if needed
                            Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findById(new BlockMetaDataId(block));
                            if (restoreBlockDataOptional.isPresent()) {
                                List<RestorePath> restorePaths = this.restorePathRepository.findByRestoreBlockData(restoreBlockDataOptional.get());
                                for (RestorePath path : restorePaths) {
                                    path.getMissingBlocks().removeIf(b -> b.getBlockMetaDataId().getBlockMetaData().getId().equals(block.getId()));
                                }
                                this.restoreBlockDataRepository.delete(restoreBlockDataOptional.get());
                            }
                        });
                    } else {
                        Optional<NettyClient> clientOptional = this.getUserForRestore(block);
                        if (clientOptional.isPresent()) {
                            var request = RestoreBlock.newBuilder().addId(blockId).setFor(RestoreBlockFor.RESTORE);
                            ProtocolMessage message = ProtocolMessage.newBuilder().setRestoreBlock(request).build();
                            try {
                                clientOptional.get().write(message);
                            } catch (RuntimeException e) {
                                LOGGER.warn("unable to send restore-block for block {} to {}", blockId, clientOptional.get().getUser().getId());
                            }
                        } else {
                            LOGGER.debug("no client to request block {} online", blockId);
                        }
                    }
                }
            }
        }

        LOGGER.info("finished restoring blocks and files from available users");
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
        Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findById(new BlockMetaDataId(bmd));
        if (restoreBlockDataOptional.isPresent()) {
            RestoreBlockData restoreBlockData = restoreBlockDataOptional.get();
            LOGGER.debug("block {} has restoration-type {}", blockId, restoreBlockData.getType());
            if (restoreBlockData.getType() == RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA || restoreBlockData.getType() == RestoreType.RESTORE) {
                LOGGER.debug("save block {} in restoration-storage", blockId);
                this.restorationStorageService.saveInLocalStorage(blockId, data.duplicate());
                List<RestorePath> restorePaths = this.restorePathRepository.findByRestoreBlockData(restoreBlockData);
                for (RestorePath path : restorePaths) {
                    path.getMissingBlocks().removeIf(b -> b.getBlockMetaDataId().getBlockMetaData().getId().equals(bmd.getId()));
                }
            }
            this.restoreBlockDataRepository.delete(restoreBlockData);
        }

        LOGGER.trace("end saveBlock");
    }
}
