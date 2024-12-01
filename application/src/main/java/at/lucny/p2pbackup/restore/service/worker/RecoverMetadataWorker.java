package at.lucny.p2pbackup.restore.service.worker;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.backup.dto.PathDataVersion;
import at.lucny.p2pbackup.core.domain.*;
import at.lucny.p2pbackup.core.repository.BlockMetaDataRepository;
import at.lucny.p2pbackup.core.repository.PathDataRepository;
import at.lucny.p2pbackup.core.repository.RootDirectoryRepository;
import at.lucny.p2pbackup.restore.dto.PathDataAndVersion;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@Validated
public class RecoverMetadataWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecoverMetadataWorker.class);

    private final RootDirectoryRepository rootDirectoryRepository;

    private final PathDataRepository pathDataRepository;

    private final BlockMetaDataRepository blockMetaDataRepository;

    private final P2PBackupProperties p2PBackupProperties;

    public RecoverMetadataWorker(RootDirectoryRepository rootDirectoryRepository, PathDataRepository pathDataRepository, BlockMetaDataRepository blockMetaDataRepository, P2PBackupProperties p2PBackupProperties) {
        this.rootDirectoryRepository = rootDirectoryRepository;
        this.pathDataRepository = pathDataRepository;
        this.blockMetaDataRepository = blockMetaDataRepository;
        this.p2PBackupProperties = p2PBackupProperties;
    }

    @Transactional
    public BlockMetaData createOrUpdateBlockMetaDataAndAddLocation(String blockId, String userId) {
        this.createOrUpdateBlockMetaData(blockId, null);
        return this.addLocation(userId, blockId);
    }

    @Transactional
    public BlockMetaData createOrUpdateBlockMetaData(String blockId, String hash) {
        // create new block meta data if none exists for the block id
        Optional<BlockMetaData> bmdOptional = this.blockMetaDataRepository.findById(blockId);
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
        return bmd;
    }

    @Transactional
    public PathDataAndVersion recoverPathVersion(String blockId, PathDataVersion pathDataVersion) {
        RootDirectory rootDirectory = this.rootDirectoryRepository.findById(pathDataVersion.getRootDirectoryId()).orElseThrow(() -> new IllegalStateException("Unknown root directory with id " + pathDataVersion.getRootDirectoryId()));
        PathData pathData = this.pathDataRepository.findByRootDirectoryAndPath(rootDirectory, pathDataVersion.getPath()).orElse(new PathData(rootDirectory, pathDataVersion.getPath()));

        LocalDateTime timestampOfVersion = LocalDateTime.ofInstant(Instant.ofEpochMilli(pathDataVersion.getDate()), ZoneOffset.UTC);
        Optional<PathVersion> versionOptional = pathData.getVersions().stream().filter(v -> v.getDate().isEqual(timestampOfVersion)).findFirst();
        if (versionOptional.isPresent()) {
            return new PathDataAndVersion(pathData, versionOptional.get());
        }

        PathVersion version = null;
        if (pathDataVersion.getDeleted()) {
            version = new PathVersion(timestampOfVersion, true);
        } else {
            version = new PathVersion(timestampOfVersion, pathDataVersion.getHash());

            for (String dataBlockId : pathDataVersion.getBlockIdsList()) {
                BlockMetaData bmd = this.createOrUpdateBlockMetaData(dataBlockId, null);
                version.getBlocks().add(bmd);
            }
        }
        version.setVersionBlock(this.createOrUpdateBlockMetaData(blockId, null));

        pathData.getVersions().add(version);
        pathData = this.pathDataRepository.save(pathData);
        return new PathDataAndVersion(pathData, version);
    }

    @Transactional
    public BlockMetaData addLocation(String userId, String blockId) {// set the user if its set for the block
        BlockMetaData bmd = this.blockMetaDataRepository.getById(blockId);
        if (userId != null && bmd.getLocations().stream().filter(l -> l.getUserId().equals(userId)).findFirst().isEmpty()) {
            // if the user is not already added as location we have to add him
            bmd.getLocations().add(new DataLocation(bmd, userId, LocalDateTime.now(ZoneOffset.UTC).minus(this.p2PBackupProperties.getVerificationProperties().getDurationBeforeVerificationInvalid())));
        }
        return bmd;
    }

    public Optional<PathDataVersion> parsePathDataVersion(ByteBuffer data) {
        try {
            PathDataVersion pathDataVersion = PathDataVersion.parseFrom(data.duplicate());
            return Optional.of(pathDataVersion);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.trace("given data was not of type PathDataVersion: {}", e.getMessage());
            return Optional.empty();
        }
    }

}
