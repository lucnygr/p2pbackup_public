package at.lucny.p2pbackup.backup.service;

import at.lucny.p2pbackup.backup.dto.BackupIndex;
import at.lucny.p2pbackup.backup.dto.BackupRootDirectory;
import at.lucny.p2pbackup.backup.dto.Block;
import at.lucny.p2pbackup.backup.dto.PathDataVersion;
import at.lucny.p2pbackup.backup.support.BackupConstants;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.PathData;
import at.lucny.p2pbackup.core.domain.PathVersion;
import at.lucny.p2pbackup.core.domain.RootDirectory;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.PathDataRepository;
import at.lucny.p2pbackup.core.service.BlockEncryptionService;
import at.lucny.p2pbackup.localstorage.dto.LocalStorageEntry;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.upload.service.CloudUploadService;
import at.lucny.p2pbackup.upload.service.DistributionService;
import at.lucny.p2pbackup.verification.service.VerificationValueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Validated
class BackupServiceWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupServiceWorker.class);

    private final PathDataRepository pathDataRepository;

    private final BlockMetaDataRepository blockMetaDataRepository;

    private final LocalStorageService localStorageService;

    private final BlockEncryptionService blockEncryptionService;

    private final VerificationValueService verificationValueService;

    private final DistributionService distributionService;

    private final CloudUploadService cloudUploadService;

    public BackupServiceWorker(PathDataRepository pathDataRepository, BlockMetaDataRepository blockMetaDataRepository, LocalStorageService localStorageService, BlockEncryptionService blockEncryptionService, VerificationValueService verificationValueService, DistributionService distributionService, CloudUploadService cloudUploadService) {
        this.pathDataRepository = pathDataRepository;
        this.blockMetaDataRepository = blockMetaDataRepository;
        this.localStorageService = localStorageService;
        this.blockEncryptionService = blockEncryptionService;
        this.verificationValueService = verificationValueService;
        this.distributionService = distributionService;
        this.cloudUploadService = cloudUploadService;
    }

    @Transactional
    public BlockMetaData addPathMissingVersionRecord(String missingPathId) {
        LOGGER.trace("begin addPathMissingVersionRecord({})", missingPathId);
        PathData pathData = this.pathDataRepository.findByIdFetchVersions(missingPathId).orElseThrow(() -> new IllegalStateException("PathData with id " + missingPathId + " not found"));

        Instant now = Instant.now();
        PathVersion version = new PathVersion(LocalDateTime.ofInstant(now, ZoneOffset.UTC), Boolean.TRUE);
        PathDataVersion pathDataVersion = PathDataVersion.newBuilder()
                .setRootDirectoryId(pathData.getRootDirectory().getId())
                .setPath(pathData.getPath())
                .setDate(now.toEpochMilli())
                .setDeleted(version.getDeleted()).build();
        byte[] pathDataVersionAsBytes = pathDataVersion.toByteArray();
        BlockMetaData bmd = this.blockMetaDataRepository.save(new BlockMetaData());
        version.setVersionBlock(bmd);

        this.encryptAndProcess(bmd, ByteBuffer.wrap(pathDataVersionAsBytes), true, true);

        pathData.getVersions().add(version);
        pathData = this.pathDataRepository.save(pathData);
        LOGGER.debug("persisted deletion for path-data {}", pathData);

        LOGGER.trace("end addPathMissingVersionRecord: return {}", bmd);
        return bmd;
    }

    @Transactional
    public BlockMetaData addPathChangedVersionRecord(RootDirectory rootDirectory, Path path, PathVersion version) {
        LOGGER.trace("begin addPathChangedVersionRecord({}, {}, {}", rootDirectory, path, version);
        Optional<PathData> optionalPathData = this.pathDataRepository.findByRootDirectoryAndPath(rootDirectory, path.toString());

        // create new PathData if it doesnt exist in the database (it's a new file)
        PathData pathData = optionalPathData.orElse(new PathData(rootDirectory, path));

        byte[] pathDataVersionAsBytes = PathDataVersion.newBuilder()
                .setRootDirectoryId(pathData.getRootDirectory().getId())
                .setPath(pathData.getPath())
                .setDate(version.getDate().toInstant(ZoneOffset.UTC).toEpochMilli())
                .setHash(version.getHash())
                .addAllBlockIds(version.getBlocks().stream().map(BlockMetaData::getId).toList()).build().toByteArray();
        BlockMetaData bmd = this.blockMetaDataRepository.save(new BlockMetaData());
        version.setVersionBlock(bmd);

        this.encryptAndProcess(bmd, ByteBuffer.wrap(pathDataVersionAsBytes), true, true);

        pathData.getVersions().add(version);
        pathData = this.pathDataRepository.save(pathData);
        LOGGER.trace("persisted path-data {}", pathData);
        LOGGER.trace("end addPathChangedVersionRecord: return {}", bmd);
        return bmd;
    }

    @Transactional
    public BlockMetaData createBlockMetaData(Block block) {
        LOGGER.trace("begin createBlockMetaData({})", block);

        Optional<BlockMetaData> optionalBlockMetaData = this.blockMetaDataRepository.findByHashFetchLocations(block.hash());
        // if the block doesn't exist in the db generate a new BlockMetaData
        BlockMetaData bmd = optionalBlockMetaData.orElseGet(() -> this.blockMetaDataRepository.save(new BlockMetaData(block.hash())));

        boolean saveInLocalBackup = optionalBlockMetaData.map(b -> this.blockMetaDataRepository.hasNotEnoughVerifiedReplicas(b.getId(), BackupConstants.NR_OF_REPLICAS, this.distributionService.calulateVerificationInvalidDateTime())).orElse(true);
        boolean needsMoreVerificationValues = this.verificationValueService.needsGenerationOfVerificationValues(bmd.getId());

        if (saveInLocalBackup || needsMoreVerificationValues) {
            this.encryptAndProcess(bmd, block.content(), saveInLocalBackup, needsMoreVerificationValues);
        }

        LOGGER.trace("end createBlockMetaData: return {}", bmd);
        return bmd;
    }

    @Transactional
    public BlockMetaData addBackupIndexBlock(List<RootDirectory> directories, Set<String> versionBlockIds) {
        long now = Instant.now().toEpochMilli();

        var builder = BackupIndex.newBuilder()
                .setDate(now)
                .addAllRootDirectories(directories.stream()
                        .map(d -> BackupRootDirectory.newBuilder().setId(d.getId()).setName(d.getName()).build())
                        .toList())
                .addAllVersionBlockIds(versionBlockIds);
        byte[] backupIndexAsBytes = builder.build().toByteArray();

        BlockMetaData bmd = this.blockMetaDataRepository.save(new BlockMetaData(BackupConstants.BACKUP_INDEX_BLOCK_PREFIX + now, (String) null));
        this.encryptAndProcess(bmd, ByteBuffer.wrap(backupIndexAsBytes), true, true);

        return bmd;
    }

    private void encryptAndProcess(BlockMetaData bmd, ByteBuffer block, boolean saveInLocalBackup, boolean generateVerificationValues) {
        LOGGER.debug("persisting local-backup-block for {}", bmd.getId());

        this.blockEncryptionService.encrypt(block.duplicate(), bmd.getId().getBytes(StandardCharsets.UTF_8), encryptedDataBuffer -> {
            if (saveInLocalBackup) {
                LocalStorageEntry localStorageEntry = this.localStorageService.saveInLocalStorage(bmd.getId(), encryptedDataBuffer.duplicate());
                this.cloudUploadService.saveCloudUpload(bmd, localStorageEntry.macSecret(), localStorageEntry.mac());
            }
            if (generateVerificationValues) {
                this.verificationValueService.ensureVerificationValues(bmd.getId(), encryptedDataBuffer.duplicate());
            }
        });
    }
}
