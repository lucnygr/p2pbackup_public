package at.lucny.p2pbackup.backup.service;

import at.lucny.p2pbackup.backup.dto.Block;
import at.lucny.p2pbackup.backup.service.worker.BackupServiceWorker;
import at.lucny.p2pbackup.backup.support.BackupFileEvent;
import at.lucny.p2pbackup.core.domain.BlockMetaData;
import at.lucny.p2pbackup.core.domain.PathVersion;
import at.lucny.p2pbackup.core.domain.RootDirectory;
import at.lucny.p2pbackup.core.repository.PathDataIdAndPath;
import at.lucny.p2pbackup.core.repository.PathDataRepository;
import at.lucny.p2pbackup.core.repository.RootDirectoryRepository;
import at.lucny.p2pbackup.core.support.HashUtils;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Validated
public class BackupServiceImpl implements BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupServiceImpl.class);

    private final HashUtils hashUtils = new HashUtils();

    private final RootDirectoryRepository rootDirectoryRepository;

    private final PathDataRepository pathDataRepository;

    private final ChunkerService chunkerService;

    private final BackupServiceWorker backupServiceWorker;

    private final ApplicationEventPublisher applicationEventPublisher;

    public BackupServiceImpl(RootDirectoryRepository rootDirectoryRepository, PathDataRepository pathDataRepository, ChunkerService chunkerService, BackupServiceWorker backupServiceWorker, ApplicationEventPublisher applicationEventPublisher) {
        this.rootDirectoryRepository = rootDirectoryRepository;
        this.pathDataRepository = pathDataRepository;
        this.chunkerService = chunkerService;
        this.backupServiceWorker = backupServiceWorker;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public Optional<RootDirectory> addRootDirectory(String name, String path) {
        if (this.rootDirectoryRepository.findByName(name).isPresent()) {
            LOGGER.info("Name {} already exists", name);
            return Optional.empty();
        }

        List<RootDirectory> directoryList = this.rootDirectoryRepository.findByPathStartsWithIgnoreCase(path);
        if (!CollectionUtils.isEmpty(directoryList)) {
            LOGGER.info("Path {} already added", path);
            return Optional.empty();
        }

        Path directory = Paths.get(path);
        if (!Files.isDirectory(directory)) {
            LOGGER.info("Path {} is not a directory", path);
            return Optional.empty();
        }

        RootDirectory rootDirectory = new RootDirectory(name, path);
        rootDirectory = this.rootDirectoryRepository.save(rootDirectory);
        LOGGER.info("RootDirectory {} saved", rootDirectory.getPath());

        return Optional.of(rootDirectory);
    }

    @Transactional(readOnly = true)
    @Override
    public List<RootDirectory> getRootDirectories() {
        return this.rootDirectoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<RootDirectory> getRootDirectory(String name) {
        return this.rootDirectoryRepository.findByName(name);
    }

    @Override
    public void backup() {
        List<RootDirectory> directories = this.rootDirectoryRepository.findAll();
        Set<String> versionBlockIds = new HashSet<>();
        for (RootDirectory rd : directories) {
            LOGGER.info("backing up root-directory {}", rd);
            versionBlockIds.addAll(this.backupRootDirectory(rd));
        }

        this.backupServiceWorker.addBackupIndexBlock(directories, versionBlockIds);
    }

    static record PathDataWrapper(Path absoluteFilePath, Path relativeFilePath, String hash, boolean changed) {
    }

    @SneakyThrows
    @Override
    public Set<String> backupRootDirectory(RootDirectory rootDirectory) {
        LOGGER.info("backup of path {}", rootDirectory.getPath());

        List<PathDataIdAndPath> paths = this.pathDataRepository.findAllByRootDirectoryAndNotDeleted(rootDirectory);
        var pathsThatNoLongerExist = paths.stream().collect(Collectors.toMap(PathDataIdAndPath::path, PathDataIdAndPath::id));
        AtomicInteger nrOfFiles = new AtomicInteger();

        try (Stream<Path> allPaths = Files.walk(Paths.get(rootDirectory.getPath()))) {
            allPaths.filter(p -> !Files.isDirectory(p)).filter(Files::isReadable) // ignore directories and unreadable files
                    .map(path -> {
                        if ((nrOfFiles.incrementAndGet() % 100) == 0) {
                            LOGGER.info("processed {} files", nrOfFiles.get());
                        }
                        Path absoluteFilePath = path.toAbsolutePath();
                        Path relativeFilePath = Paths.get(rootDirectory.getPath()).relativize(absoluteFilePath);
                        pathsThatNoLongerExist.remove(relativeFilePath.toString());

                        // generate hash for file and check if the latest seen version has the same hash (and is therefore unchanged)
                        String hash = this.hashUtils.generateHashForFile(absoluteFilePath);
                        boolean fileUnchanged = this.pathDataRepository.isLatestPathDataVersionHashSame(rootDirectory, relativeFilePath.toString(), hash);
                        LOGGER.debug("file {} has hash {}. comparing to latest seen version: changed={}", absoluteFilePath, hash, !fileUnchanged);
                        return new PathDataWrapper(absoluteFilePath, relativeFilePath, hash, !fileUnchanged);
                    })
                    .filter(PathDataWrapper::changed) // only keep changed files
                    .forEach(wrapper -> {
                        PathVersion version = new PathVersion(LocalDateTime.now(ZoneOffset.UTC), wrapper.hash());
                        Iterator<Block> blockIterator = this.chunkerService.createIterator(wrapper.absoluteFilePath);

                        while (blockIterator.hasNext()) {
                            Block block = blockIterator.next();
                            BlockMetaData bmd = this.backupServiceWorker.createBlockMetaData(block);
                            version.getBlocks().add(bmd);
                        }

                        LOGGER.debug("backup new version for {}", wrapper.absoluteFilePath);
                        this.backupServiceWorker.addPathChangedVersionRecord(rootDirectory, wrapper.relativeFilePath, version);

                        this.applicationEventPublisher.publishEvent(new BackupFileEvent(this, wrapper.absoluteFilePath));
                    });
        }

        LOGGER.info("processed {} files, checking for deleted files", nrOfFiles.get());

        for (String missingPathId : pathsThatNoLongerExist.values()) {
            LOGGER.debug("backup deleted version for path-id {}", missingPathId);
            this.backupServiceWorker.addPathMissingVersionRecord(missingPathId);
        }

        LOGGER.info("backup of path {} finished", rootDirectory.getPath());

        return this.pathDataRepository.getLatestPathVersionsByRootDirectory(rootDirectory);
    }


}
