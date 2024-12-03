package at.lucny.p2pbackup.cloud.filesystem.service;

import at.lucny.p2pbackup.cloud.CloudStorageService;
import lombok.SneakyThrows;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Validated
public class FilesystemStorageServiceImpl implements CloudStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilesystemStorageServiceImpl.class);

    public static final String PROVIDER_ID = "at.lucny.p2pbackup.cloud.filesystem.service.FilesystemStorageServiceImpl";

    public static final String CONFIG_KEY_DIRECTORY = "at.lucny.p2pbackup.cloud.filesystem.service.DIRECTORY";

    private final Configuration configuration;

    private Path storageDirectory;

    public FilesystemStorageServiceImpl(Configuration configuration) {
        this.configuration = configuration;

        if (this.configuration.containsKey(CONFIG_KEY_DIRECTORY)) {
            this.initialize();
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void configure(Map<String, String> config) {
        String directory = Objects.requireNonNull(config.get("directory"), "Missing property directory");
        this.configuration.setProperty(CONFIG_KEY_DIRECTORY, directory);

        this.initialize();
    }

    @SneakyThrows
    private void initialize() {
        String directory = Objects.requireNonNull(this.configuration.getString(CONFIG_KEY_DIRECTORY), "local storage provider not configured, property " + CONFIG_KEY_DIRECTORY + " missing");
        LOGGER.warn("cloud-service {} is configured, use this only for testing purposes", this.getId());
        this.storageDirectory = Paths.get(directory);
        Files.createDirectories(this.storageDirectory);
    }

    @Override
    public boolean isInitialized() {
        return this.storageDirectory != null;
    }

    private void checkInitialized() {
        if (!this.isInitialized()) {
            throw new IllegalStateException("filesystem-storage wasn't initialized");
        }
    }

    @SneakyThrows
    @Override
    public void upload(Path path) {
        LOGGER.trace("begin upload(path={})", path);
        this.checkInitialized();
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("path " + path + " does not exist");
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("path " + path + " is not readable");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("path " + path + " is not a regular file");
        }

        Path destinationPath = this.storageDirectory.resolve(path.getFileName().toString());
        Files.copy(path, destinationPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.trace("end upload");
    }

    @Override
    public String share(String filename) {
        LOGGER.trace("begin share(filename={})", filename);
        this.checkInitialized();
        Path pathToFile = this.storageDirectory.resolve(filename);
        if (Files.notExists(pathToFile)) {
            throw new IllegalArgumentException("path " + pathToFile + " does not exist");
        }
        if (!Files.isReadable(pathToFile)) {
            throw new IllegalArgumentException("path " + pathToFile + " is not readable");
        }
        if (!Files.isRegularFile(pathToFile)) {
            throw new IllegalArgumentException("path " + pathToFile + " is not a regular file");
        }
        String publicUrl = pathToFile.toUri().toString();
        LOGGER.trace("end share: return={}", publicUrl);
        return publicUrl;
    }

    @SneakyThrows
    @Override
    public void delete(String filename) {
        LOGGER.trace("begin share(filename={})", filename);
        this.checkInitialized();
        Path pathToFile = this.storageDirectory.resolve(filename);
        Files.deleteIfExists(pathToFile);
        LOGGER.trace("end delete");
    }

    @SneakyThrows
    public List<Path> getFiles() {
        LOGGER.trace("begin getFiles");
        this.checkInitialized();
        List<Path> files = null;
        try (Stream<Path> allPaths = Files.list(this.storageDirectory)) {
            files = allPaths.toList();
        }
        LOGGER.trace("end getFiles: return {}", files);
        return files;
    }

    @Override
    public List<String> list() {
        return this.getFiles().stream().map(p -> p.getFileName().toString()).toList();
    }
}
