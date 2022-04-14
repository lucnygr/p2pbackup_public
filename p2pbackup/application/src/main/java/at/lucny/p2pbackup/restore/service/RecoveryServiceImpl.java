package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.backup.dto.BackupIndex;
import at.lucny.p2pbackup.backup.dto.PathDataVersion;
import at.lucny.p2pbackup.backup.support.BackupConstants;
import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.configuration.support.RecoveryState;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.RootDirectory;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.RootDirectoryRepository;
import at.lucny.p2pbackup.core.support.HashUtils;
import at.lucny.p2pbackup.network.dto.ProtocolMessage;
import at.lucny.p2pbackup.network.service.ClientService;
import at.lucny.p2pbackup.network.service.NettyClient;
import at.lucny.p2pbackup.restore.domain.RecoverBackupIndex;
import at.lucny.p2pbackup.restore.domain.RecoverRootDirectory;
import at.lucny.p2pbackup.restore.domain.RestoreType;
import at.lucny.p2pbackup.restore.dto.PathDataAndVersion;
import at.lucny.p2pbackup.restore.repository.RecoverBackupIndexRepository;
import at.lucny.p2pbackup.restore.service.worker.RecoverMetadataWorker;
import at.lucny.p2pbackup.restore.service.worker.RestoreTaskWorker;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.SneakyThrows;
import lombok.Synchronized;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final BlockMetaDataRepository blockMetaDataRepository;

    private final ClientService clientService;

    private final RootDirectoryRepository rootDirectoryRepository;

    private final RecoverBackupIndexRepository recoverBackupIndexRepository;

    private final Configuration configuration;

    private final HashUtils hashUtils = new HashUtils();

    private final RestoreTaskWorker restoreTaskService;

    private final RecoverMetadataWorker recoverMetadataWorker;

    public RecoveryServiceImpl(BlockMetaDataRepository blockMetaDataRepository, @Lazy ClientService clientService, RootDirectoryRepository rootDirectoryRepository, RecoverBackupIndexRepository recoverBackupIndexRepository, Configuration configuration, RestoreTaskWorker restoreTaskService, RecoverMetadataWorker recoverMetadataWorker) {
        this.blockMetaDataRepository = blockMetaDataRepository;
        this.clientService = clientService;
        this.rootDirectoryRepository = rootDirectoryRepository;
        this.recoverBackupIndexRepository = recoverBackupIndexRepository;
        this.configuration = configuration;
        this.restoreTaskService = restoreTaskService;
        this.recoverMetadataWorker = recoverMetadataWorker;
    }

    @Override
    public void initializeRecoveryMode() {
        if (!this.configuration.containsKey(ConfigurationConstants.PROPERTY_RECOVERY_STATE)) {
            this.configuration.setProperty(ConfigurationConstants.PROPERTY_RECOVERY_STATE, RecoveryState.INITIALIZED);
        }
        LOGGER.info("application is now in recovery-mode");
    }

    @Override
    public boolean isRecoveryActive() {
        return this.configuration.containsKey(ConfigurationConstants.PROPERTY_RECOVERY_STATE);
    }

    @Override
    public void requestBackupIndex() {
        LOGGER.trace("begin requestBackupIndex()");

        this.initializeRecoveryMode();

        List<NettyClient> clients = this.clientService.getClients();
        for (NettyClient client : clients) {
            if (this.clientService.isOnline(client)) {
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

        LOGGER.trace("end requestBackupIndex()");
    }

    @Override
    @Synchronized
    public void recoverBlockMetaData(String userId, Set<String> blockIds) {
        LOGGER.trace("begin recoverBlockMetaData(userId={}, blockIds={})", userId, blockIds.size());

        LOGGER.info("user {} saves {} blocks - restore block-meta-data and add user {} as data-location", userId, blockIds.size(), userId);
        // persist all blocks that the given userId holds
        for (String blockId : blockIds) {
            this.recoverMetadataWorker.createOrUpdateBlockMetaDataAndAddLocation(blockId, userId);
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
            for (String blockId : backupIndex.getVersionBlockIdsList()) {
                this.recoverMetadataWorker.createOrUpdateBlockMetaData(blockId, null);
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
            directory = console.readLine("Please input the destination-directory for the root-directory %s:", name);
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

            LOGGER.info("recover root-directory {} to directory {}", recoverRootDirectory.getName(), rootDirectoryRecoveryDir);
            Files.createDirectories(rootDirectoryRecoveryDir);
            if (!this.rootDirectoryRepository.existsById(recoverRootDirectory.getId())) {
                this.rootDirectoryRepository.save(new RootDirectory(recoverRootDirectory.getId(), recoverRootDirectory.getName(), rootDirectoryRecoveryDir));
            }
        }
        this.rootDirectoryRepository.flush();

        // create recover-tasks for all blocks
        Pageable page = PageRequest.of(0, 100, Sort.Direction.ASC, "id");
        Page<BlockMetaData> blockMetaDataPage = this.blockMetaDataRepository.findAll(page);
        while (blockMetaDataPage.hasContent()) {
            for (BlockMetaData bmd : blockMetaDataPage.getContent()) {
                this.restoreTaskService.createOrUpdateRestoreBlockData(bmd, RestoreType.RECOVER);
            }
            if (blockMetaDataPage.hasNext()) {
                blockMetaDataPage = this.blockMetaDataRepository.findAll(blockMetaDataPage.nextPageable());
            } else {
                break;
            }
        }

        // create recover-and-restore-data-tasks for all blocks of the requested version
        for (String blockId : indexOptional.get().getVersionBlockIds()) {
            this.restoreTaskService.createOrUpdateRestoreBlockData(this.blockMetaDataRepository.getById(blockId), RestoreType.RECOVER_META_DATA_AND_RESTORE_DATA);
        }

        this.configuration.setProperty(ConfigurationConstants.PROPERTY_RECOVERY_STATE, RecoveryState.RECOVER_DATA);

        this.recoverBackupIndexRepository.deleteAll();

        LOGGER.trace("end startRecovery");
    }

    @Override
    @Transactional
    public boolean recoverMetaData(String userId, String blockId, ByteBuffer data) {
        LOGGER.trace("begin recoverMetaData(userId={}, blockId={}, data={} remaining", userId, blockId, data.remaining());

        // is the block an index-block?
        if (blockId.startsWith(BackupConstants.BACKUP_INDEX_BLOCK_PREFIX)) {
            LOGGER.debug("block {} is an index-block", blockId);
            LOGGER.trace("end recoverMetaData(userId={}, blockId={}, data={} remaining: return false", userId, blockId, data.remaining());
            return false;
        }

        // is the block a path-data-version-block?
        Optional<PathDataVersion> optionalPathDataVersion = this.recoverMetadataWorker.parsePathDataVersion(data);
        if (optionalPathDataVersion.isPresent()) {
            LOGGER.debug("block {} is a PathDataVersion-block", blockId);
            PathDataAndVersion pathDataAndVersion = this.recoverMetadataWorker.recoverPathVersion(blockId, optionalPathDataVersion.get());
            if (userId != null) {
                this.recoverMetadataWorker.addLocation(userId, blockId);
            }
            Path restorePath = Paths.get(pathDataAndVersion.pathData().getRootDirectory().getPath()).resolve(pathDataAndVersion.pathData().getPath());
            this.restoreTaskService.updateRestoreTasksForFile(blockId, pathDataAndVersion.pathVersion(), restorePath);


            LOGGER.trace("end recoverMetaData: return false");
            return false;
        }

        // otherwise it's a data-block
        LOGGER.debug("block {} is a data-block", blockId);
        String hash = this.hashUtils.generateBlockHash(data.duplicate());
        this.recoverMetadataWorker.createOrUpdateBlockMetaData(blockId, hash);
        if (userId != null) {
            this.recoverMetadataWorker.addLocation(userId, blockId);
        }

        LOGGER.trace("end recoverMetaData: return true");
        return true;
    }
}