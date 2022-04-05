package at.lucny.p2pbackup.cloud;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

/**
 * Provider for all available cloud-storage-services.
 */
public interface CloudStorageServiceProvider {

    /**
     * Returns all available cloud-storage-services, including uninitialized ones.
     *
     * @return a list
     */
    @NotNull List<CloudStorageService> getCloudStorageServices();

    /**
     * Returns all initialized cloud-storage-services. Available cloud-storage-services that are not initialilzed are excluded
     *
     * @return a list of initialized cloud-storage-services
     */
    @NotNull List<CloudStorageService> getInitializedCloudStorageServices();

    /**
     * Returns the cloud-storage-service with the given provider id, if it's available and initialized
     *
     * @param providerId the provider-id of the cloud-storage-service
     * @return the cloud-storage-service if its available and initialized, otherwise an empty optional
     */
    @NotNull Optional<CloudStorageService> getInitializedCloudStorageService(@NotNull String providerId);
}
