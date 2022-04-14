package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.PathData;
import at.lucny.p2pbackup.core.domain.PathVersion;
import at.lucny.p2pbackup.core.domain.RootDirectory;
import at.lucny.p2pbackup.core.repository.PathDataRepository;
import at.lucny.p2pbackup.core.repository.PathVersionRepository;
import at.lucny.p2pbackup.restore.domain.RestoreBlockData;
import at.lucny.p2pbackup.restore.domain.RestorePath;
import at.lucny.p2pbackup.restore.domain.RestoreType;
import at.lucny.p2pbackup.restore.repository.RestoreBlockDataRepository;
import at.lucny.p2pbackup.restore.repository.RestorePathRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Validated
@Service
public class RestoreManagementServiceImpl implements RestoreManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreManagementServiceImpl.class);

    private final PathDataRepository pathDataRepository;

    private final PathVersionRepository pathVersionRepository;

    private final RestorePathRepository restorePathRepository;

    private final RestoreBlockDataRepository restoreBlockDataRepository;

    public RestoreManagementServiceImpl(PathDataRepository pathDataRepository, PathVersionRepository pathVersionRepository, RestorePathRepository restorePathRepository, RestoreBlockDataRepository restoreBlockDataRepository) {
        this.pathDataRepository = pathDataRepository;
        this.pathVersionRepository = pathVersionRepository;
        this.restorePathRepository = restorePathRepository;
        this.restoreBlockDataRepository = restoreBlockDataRepository;
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
                RestoreBlockData restoreBlockData = this.restoreBlockDataRepository.findByBlockMetaDataId(blockMetaData.getId()).orElse(this.restoreBlockDataRepository.save(new RestoreBlockData(blockMetaData, RestoreType.RESTORE)));
                restorePath.getMissingBlocks().add(restoreBlockData);
            }
            this.restorePathRepository.save(restorePath);
        }

        LOGGER.trace("end beginRestore(rootDirectory={}, forDate={}, targetDir={}", rootDirectory, forDate, targetDir);
    }
}
