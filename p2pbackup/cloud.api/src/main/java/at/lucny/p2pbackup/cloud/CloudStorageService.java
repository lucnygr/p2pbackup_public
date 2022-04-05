package at.lucny.p2pbackup.cloud;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.Map;

public interface CloudStorageService {

    /**
     * Returns the id of the cloud-storage-service.
     *
     * @return the id
     */
    @NotNull String getId();

    /**
     * Configure the cloud-storage-service from properties of the given map. The properties depend on the cloud-storage-provider.
     *
     * @param config a map with properties
     */
    void configure(@NotNull Map<String, String> config);

    /**
     * Is the cloud-storage-service initialized to use as cloud-storage
     *
     * @return true if the service is usable, otherwise false
     */
    boolean isInitialized();

    /**
     * Uploads the file under the given path to the cloud-storage.
     *
     * @param path the path to a local file in the filesystem
     */
    void upload(@NotNull Path path);

    /**
     * Searches for the file in the cloud-storage and shares it read-only via a generated public url.
     *
     * @param filename the filename in the cloud-storage
     * @return the url to publicly access the file
     */
    @NotNull String share(@NotNull String filename);

    /**
     * Searches for the file in the cloud-storage and deletes it from the cloud-storage.
     *
     * @param filename the filename in the cloud-storage
     */
    void delete(@NotNull String filename);
}
