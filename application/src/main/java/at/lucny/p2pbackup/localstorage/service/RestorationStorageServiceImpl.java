package at.lucny.p2pbackup.localstorage.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Validated
@Service
public class RestorationStorageServiceImpl implements RestorationStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestorationStorageServiceImpl.class);

    private static final String FOLDER_RESTORE = "_RESTORE";

    private final P2PBackupProperties p2PBackupProperties;

    public RestorationStorageServiceImpl(P2PBackupProperties p2PBackupProperties) throws IOException {
        this.p2PBackupProperties = p2PBackupProperties;
        Files.createDirectories(p2PBackupProperties.getStorageDir().resolve(FOLDER_RESTORE));
    }

    @Override
    public Optional<Path> saveInLocalStorage(String id, ByteBuffer block) {
        LOGGER.trace("begin saveInLocalStorageForRestore({}, {})", id, block);

        Path blockPath = this.p2PBackupProperties.getStorageDir().resolve(FOLDER_RESTORE).resolve(id);
        if (Files.notExists(blockPath)) {
            LOGGER.debug("persisting local-backup-block for {} for restore", id);

            try (WritableByteChannel fileChannel = FileChannel.open(blockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                fileChannel.write(block.duplicate());
            } catch (IOException e) {
                throw new IllegalStateException("unable to write block to storage file " + blockPath, e);
            }
        } else {
            LOGGER.debug("local-backup-block for {} for restore already exists", id);
            return Optional.empty();
        }

        LOGGER.trace("end saveInLocalStorageForRestore");
        return Optional.of(blockPath);
    }

    @Override
    public Optional<Path> loadFromLocalStorage(String id) {
        Path blockFile = this.p2PBackupProperties.getStorageDir().resolve(FOLDER_RESTORE).resolve(id);
        if (Files.exists(blockFile)) {
            return Optional.of(blockFile);
        }
        return Optional.empty();
    }

    @Override
    public boolean deleteAll() {
        Path directory = this.p2PBackupProperties.getStorageDir().resolve(FOLDER_RESTORE);
        try {
            FileUtils.cleanDirectory(directory.toFile());
            return true;
        } catch (IOException e) {
            LOGGER.warn("unable to clear restore-directory {}", directory);
        }
        return false;
    }

    @SneakyThrows
    public List<Path> getFiles() {
        LOGGER.trace("begin getFiles");
        List<Path> files = null;
        try (Stream<Path> allPaths = Files.list(this.p2PBackupProperties.getStorageDir().resolve(FOLDER_RESTORE))) {
            files = allPaths.toList();
        }
        LOGGER.trace("end getFiles: return {}", files);
        return files;
    }
}
