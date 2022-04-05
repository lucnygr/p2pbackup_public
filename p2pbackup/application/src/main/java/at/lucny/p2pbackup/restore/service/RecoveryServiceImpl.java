package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.backup.dto.BackupIndex;
import at.lucny.p2pbackup.backup.dto.PathDataVersion;
import at.lucny.p2pbackup.backup.support.BackupConstants;
import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.configuration.support.RecoveryState;
import at.lucny.p2pbackup.core.domain.*;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.PathDataRepository;
import at.lucny.p2pbackup.core.repository.RootDirectoryRepository;
import at.lucny.p2pbackup.core.support.HashUtils;
import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.service.ClientService;
import at.lucny.p2pbackup.network.service.NettyClient;
import at.lucny.p2pbackup.restore.domain.*;
import at.lucny.p2pbackup.restore.repository.RecoverBackupIndexRepository;
import at.lucny.p2pbackup.restore.repository.RestoreBlockDataRepository;
import at.lucny.p2pbackup.restore.repository.RestorePathRepository;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.SneakyThrows;
import lombok.Synchronized;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.io.Console;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Validated
public class RecoveryServiceImpl implements RecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryServiceImpl.class);

    private final P2PBackupProperties p2PBackupProperties;

    private final PlatformTransactionManager txManager;

    private final BlockMetaDataRepository blockMetaDataRepository;

    private final RestoreBlockDataRepository restoreBlockDataRepository;

    private final ClientService clientService;

    private final RootDirectoryRepository rootDirectoryRepository;

    private final PathDataRepository pathDataRepository;

    private final RecoverBackupIndexRepository recoverBackupIndexRepository;

    private final RestorePathRepository restorePathRepository;
    private final Configuration configuration;

    private final HashUtils hashUtils = new HashUtils();

    public RecoveryServiceImpl(P2PBackupProperties p2PBackupProperties, PlatformTransactionManager txManager, BlockMetaDataRepository blockMetaDataRepository, RestoreBlockDataRepository restoreBlockDataRepository, @Lazy ClientService clientService, RootDirectoryRepository rootDirectoryRepository, PathDataRepository pathDataRepository, RecoverBackupIndexRepository recoverBackupIndexRepository, RestorePathRepository restorePathRepository, Configuration configuration) {
        this.p2PBackupProperties = p2PBackupProperties;
        this.txManager = txManager;
        this.blockMetaDataRepository = blockMetaDataRepository;
        this.restoreBlockDataRepository = restoreBlockDataRepository;
        this.clientService = clientService;
        this.rootDirectoryRepository = rootDirectoryRepository;
        this.pathDataRepository = pathDataRepository;
        this.recoverBackupIndexRepository = recoverBackupIndexRepository;
        this.restorePathRepository = restorePathRepository;
        this.configuration = configuration;
    }

    @Override
    public void recoverBackupIndex() {
        LOGGER.trace("begin recoverBackupIndex");

        this.configuration.setProperty(ConfigurationConstants.PROPERTY_RECOVERY_STATE, RecoveryState.INITIALIZED);

        List<NettyClient> clients = this.clientService.getClients();
        for (NettyClient client : clients) {
            if (client.isConnected()) {
                LOGGER.debug("sending request to recover backup-index to {}", client.getUser().getId());
                var request = at.lucny.p2pbackup.network.dto.RecoverBackupIndex.newBuilder().build();
                ProtocolMessage message = ProtocolMessage.newBuilder().setRecoverBackupIndex(request).build();
                try {
                    client.write(message);
                } catch (RuntimeException e) {
                    LOGGER.warn("unable to query blocks from user {}: {}", client.getUser().getId(), e.getMessage());
                }
            } else {
                LOGGER.info("user {} is offline, please request to go online", client.getUser().getId());
            }
        }

        LOGGER.trace("end recoverBackupIndex");
    }

    @Override
    @Synchronized
    public void recoverBlockMetaData(String userId, Set<String> blockIds) {
        LOGGER.trace("begin recoverBlockMetaData(userId={}, blockIds={})", userId, blockIds.size());

        LOGGER.info("user {} saves {} blocks - restore block-meta-data and add user {} as data-location", userId, blockIds.size(), userId);
        TransactionTemplate template = new TransactionTemplate(this.txManager);
        // persist all blocks that the given userId holds
        for (String blockId : blockIds) {
            template.executeWithoutResult(status -> {
                BlockMetaData bmd = this.createOrUpdateBlockMetaData(userId, blockId, null);
                this.createOrUpdateRestoreBlockData(bmd, RestoreType.RECOVER);
            });
        }

        LOGGER.trace("end recoverBlockMetaData");
    }

    @Override
    @Synchronized
    public void recoverBackupIndex(String userId, ByteBuffer backupIndexByteArray) {
        LOGGER.trace("begin recoverBackupIndex(userId={}, backupIndexByteArray={})", userId, backupIndexByteArray.remaining());

        try {
            BackupIndex backupIndex = BackupIndex.parseFrom(backupIndexByteArray.duplicate());
            LocalDateTime backupDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(backupIndex.getDate()), ZoneOffset.UTC);
            LOGGER.info("recover backup-index {}/{} from user {}", backupIndex.getDate(), backupDate, userId);

            if (!this.recoverBackupIndexRepository.existsByDate(backupDate)) {
                Set<RecoverRootDirectory> recoverRootDirectories = backupIndex.getRootDirectoriesList().stream().map(rd -> new RecoverRootDirectory(rd.getId(), rd.getName())).collect(Collectors.toSet());
                this.recoverBackupIndexRepository.save(new RecoverBackupIndex(backupDate, recoverRootDirectories, new HashSet<>(backupIndex.getVersionBlockIdsList())));
                LOGGER.debug("backup-index {}/{} from user {} saved", backupIndex.getDate(), backupDate, userId);
            } else {
                LOGGER.debug("backup-index {}/{} already known, skip saving", backupIndex.getDate(), backupDate);
            }

            LOGGER.debug("saving block-meta-data for {} version-blocks", backupIndex.getVersionBlockIdsList().size());
            TransactionTemplate template = new TransactionTemplate(this.txManager);
            for (String blockId : backupIndex.getVersionBlockIdsList()) {
                template.executeWithoutResult(status -> {
                    BlockMetaData bmd = this.createOrUpdateBlockMetaData(null, blockId, null);
                    this.createOrUpdateRestoreBlockData(bmd, RestoreType.RECOVER);
                });
            }

            LOGGER.info("recovered backup-index {}/{} from user {}", backupIndex.getDate(), backupDate, userId);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.warn("unable to parse backup-index from user {}: {}", userId, e.getMessage());
            LOGGER.trace("unable to parse backup-index from user {}", userId, e);
        }

        LOGGER.trace("end recoverBackupIndex");
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecoverBackupIndex> getAllRestoreBackupIndizes() {
        return this.recoverBackupIndexRepository.findAll();
    }

    private String readDestinationDirectoryForRootDirectory(String name) {
        String directory = null;
        Console console = System.console();
        if (console != null) {
            directory = console.readLine("Please input the destination-directory for the root-directory {}:", name);
        } else {
            System.out.println("Please input the destination-directory for the root-directory " + name + ":");
            Scanner scanner = new Scanner(System.in);
            directory = scanner.nextLine();
        }
        return directory;
    }

    @Override
    @Transactional
    @SneakyThrows
    public void startRecovery(String idOfBackupIndex) {
        LOGGER.trace("begin startRecovery(idOfBackupIndex={})", idOfBackupIndex);

        Optional<RecoverBackupIndex> indexOptional = this.recoverBackupIndexRepository.findById(idOfBackupIndex);
        if (indexOptional.isEmpty()) {
            LOGGER.warn("backup-index with id {} does not exist", idOfBackupIndex);
            return;
        }
        RecoverBackupIndex index = indexOptional.get();

        LOGGER.info("start recovery for backup with id {} and date {}", idOfBackupIndex, index.getDate());

        for (RecoverRootDirectory recoverRootDirectory : index.getRootDirectories()) {
            String destinationDirectory = this.readDestinationDirectoryForRootDirectory(recoverRootDirectory.getName());
            Path rootDirectoryRecoveryDir = Paths.get(destinationDirectory);
            if (!Files.isDirectory(rootDirectoryRecoveryDir)) {
                LOGGER.info("recover-directory {} is not a directory", rootDirectoryRecoveryDir);
                return;
            }

            LOGGER.debug("recover root-directory {} to directory {}", recoverRootDirectory.getName(), rootDirectoryRecoveryDir);
            Files.createDirectories(rootDirectoryRecoveryDir);
            if (!this.rootDirectoryRepository.existsById(recoverRootDirectory.getId())) {
                this.rootDirectoryRepository.save(new RootDirectory(recoverRootDirectory.getId(), recoverRootDirectory.getName(), rootDirectoryRecoveryDir));
            }
        }

        for (String blockId : indexOptional.get().getVersionBlockIds()) {
            this.createOrUpdateRestoreBlockData(this.blockMetaDataRepository.getById(blockId), RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA);
        }

        this.configuration.setProperty(ConfigurationConstants.PROPERTY_RECOVERY_STATE, RecoveryState.RECOVER_DATA);

        // todo delete all?
        for (RecoverBackupIndex unusedIndex : this.recoverBackupIndexRepository.findByIdNot(idOfBackupIndex)) {
            this.recoverBackupIndexRepository.delete(unusedIndex);
        }

        LOGGER.trace("end startRecovery");
    }

    private BlockMetaData createOrUpdateBlockMetaData(String userId, String blockId, String hash) {
        // create new block meta data if none exists for the block id
        Optional<BlockMetaData> bmdOptional = this.blockMetaDataRepository.findByIdFetchLocations(blockId);
        BlockMetaData bmd = null;
        if (bmdOptional.isEmpty()) {
            bmd = this.blockMetaDataRepository.save(new BlockMetaData(blockId, hash));
        } else {
            bmd = bmdOptional.get();
            if (hash != null) {
                if (bmd.getHash() != null && !hash.equals(bmd.getHash())) {
                    throw new IllegalStateException("block " + blockId + " already has hash " + bmd.getHash() + ", but the hash " + hash + " should be set");
                }
                bmd.setHash(hash);
            }
        }

        // set the user if its set for the block
        if (userId != null && bmd.getLocations().stream().filter(l -> l.getUserId().equals(userId)).findFirst().isEmpty()) {
            // if the user is not already added as location we have to add him
            bmd.getLocations().add(new DataLocation(bmd, userId, LocalDateTime.now(ZoneOffset.UTC).minus(this.p2PBackupProperties.getVerificationProperties().getDurationBeforeVerificationInvalid())));
        }

        return bmd;
    }

    private RestoreBlockData createOrUpdateRestoreBlockData(BlockMetaData bmd, RestoreType restoreType) {
        BlockMetaDataId bmdId = new BlockMetaDataId(bmd);
        Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findById(bmdId);
        if (restoreBlockDataOptional.isEmpty()) {
            return this.restoreBlockDataRepository.save(new RestoreBlockData(bmdId, restoreType));
        } else if (restoreType.ordinal() > restoreBlockDataOptional.get().getType().ordinal()) { // only "upgrade" the restore type
            restoreBlockDataOptional.get().setType(restoreType);
        }
        return restoreBlockDataOptional.get();
    }

    @Override
    @Transactional
    public boolean recoverMetaData(String userId, String blockId, ByteBuffer data) {
        LOGGER.trace("begin recoverMetaData(userId={}, blockId={}, data={} remaining", userId, blockId, data.remaining());

        // is the block an index-block?
        if (blockId.startsWith(BackupConstants.BACKUP_INDEX_BLOCK_PREFIX)) {
            LOGGER.debug("block {} is an index-block", blockId);
            BlockMetaData bmd = this.createOrUpdateBlockMetaData(userId, blockId, null);
            this.restoreBlockDataRepository.deleteAllById(Collections.singletonList(new BlockMetaDataId(bmd)));
            LOGGER.trace("end recoverMetaData: return false");
            return false;
        }

        // is the block a path-data-version-block?
        Optional<PathDataVersion> optionalPathDataVersion = this.parsePathDataVersion(data);
        if (optionalPathDataVersion.isPresent()) {
            LOGGER.debug("block {} is a PathDataVersion-block", blockId);
            this.recoverPathVersion(userId, blockId, optionalPathDataVersion.get());
            LOGGER.trace("end recoverMetaData: return false");
            return false;
        }

        // otherwise it's a data-block
        LOGGER.debug("block {} is a data-block", blockId);
        String hash = this.hashUtils.generateBlockHash(data.duplicate());
        this.createOrUpdateBlockMetaData(userId, blockId, hash);

        LOGGER.trace("end recoverMetaData: return true");
        return true;
    }

    private Optional<PathDataVersion> parsePathDataVersion(ByteBuffer data) {
        try {
            PathDataVersion pathDataVersion = PathDataVersion.parseFrom(data.duplicate());
            return Optional.of(pathDataVersion);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.trace("given data was not of type PathDataVersion: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void recoverPathVersion(String userId, String blockId, PathDataVersion pathDataVersion) {
        RootDirectory rootDirectory = this.rootDirectoryRepository.findById(pathDataVersion.getRootDirectoryId()).orElseThrow(() -> new IllegalStateException("Unknown root directory with id " + pathDataVersion.getRootDirectoryId()));
        PathData pathData = this.pathDataRepository.findByRootDirectoryAndPath(rootDirectory, pathDataVersion.getPath()).orElse(new PathData(rootDirectory, pathDataVersion.getPath()));

        LocalDateTime timestampOfVersion = LocalDateTime.ofInstant(Instant.ofEpochMilli(pathDataVersion.getDate()), ZoneOffset.UTC);
        if (pathData.getVersions().stream().anyMatch(v -> v.getDate().isEqual(timestampOfVersion))) {
            return;
        }

        PathVersion version = null;
        if (pathDataVersion.getDeleted()) {
            version = new PathVersion(timestampOfVersion, true);
        } else {
            version = new PathVersion(timestampOfVersion, pathDataVersion.getHash());

            for (String dataBlockId : pathDataVersion.getBlockIdsList()) {
                BlockMetaData bmd = this.createOrUpdateBlockMetaData(null, dataBlockId, null);
                version.getBlocks().add(bmd);
            }
        }
        version.setVersionBlock(this.createOrUpdateBlockMetaData(userId, blockId, null));

        pathData.getVersions().add(version);
        this.pathDataRepository.save(pathData);

        Optional<RestoreBlockData> restoreBlockDataOptional = this.restoreBlockDataRepository.findById(new BlockMetaDataId(this.blockMetaDataRepository.getById(blockId)));
        if (restoreBlockDataOptional.isPresent()) {
            // we only need to request data for not deleted files
            if (Boolean.FALSE.equals(version.getDeleted())) {
                Set<RestoreBlockData> restoreBlockDataSet = new HashSet<>();
                for (BlockMetaData bmd : version.getBlocks()) {
                    // restore blocks with the same restore-type -> if the metadata was set to restore data then the block data must also be restored
                    restoreBlockDataSet.add(this.createOrUpdateRestoreBlockData(bmd, restoreBlockDataOptional.get().getType()));
                }

                // add an entry to restore the file
                if (restoreBlockDataOptional.get().getType() == RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA) {
                    RestorePath restorePath = new RestorePath(version, Paths.get(rootDirectory.getPath()).resolve(pathData.getPath()).toString());
                    restorePath.getMissingBlocks().addAll(restoreBlockDataSet);
                    this.restorePathRepository.save(restorePath);
                }
            }
            this.restoreBlockDataRepository.deleteAllInBatch(Collections.singletonList(restoreBlockDataOptional.get()));
        }
    }
}