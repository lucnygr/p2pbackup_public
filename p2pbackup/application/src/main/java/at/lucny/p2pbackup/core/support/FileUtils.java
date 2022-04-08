package at.lucny.p2pbackup.core.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility-class for file-operations.
 */
public class FileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Deletes a path via {@link Files#delete(Path)} and logs a warning in case of an {@link IOException}.
     *
     * @param path the path to the file
     */
    public void deleteIfExistsSilent(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("unable to delete file {}", path);
        }
    }
}
