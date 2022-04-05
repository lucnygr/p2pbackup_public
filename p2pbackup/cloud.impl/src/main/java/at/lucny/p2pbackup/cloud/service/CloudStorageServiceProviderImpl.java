package at.lucny.p2pbackup.cloud.service;

import at.lucny.p2pbackup.cloud.CloudStorageService;
import at.lucny.p2pbackup.cloud.CloudStorageServiceProvider;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Validated
public class CloudStorageServiceProviderImpl implements CloudStorageServiceProvider {

    private final List<CloudStorageService> cloudStorageServiceList;

    public CloudStorageServiceProviderImpl(List<CloudStorageService> cloudStorageServiceList) {
        this.cloudStorageServiceList = cloudStorageServiceList;
    }

    @Override
    public List<CloudStorageService> getCloudStorageServices() {
        return new ArrayList<>(cloudStorageServiceList);
    }

    @Override
    public List<CloudStorageService> getInitializedCloudStorageServices() {
        return this.cloudStorageServiceList.stream().filter(CloudStorageService::isInitialized).toList();
    }

    @Override
    public Optional<CloudStorageService> getInitializedCloudStorageService(String providerId) {
        return this.cloudStorageServiceList.stream().filter(CloudStorageService::isInitialized)
                .filter(service -> service.getId().equals(providerId)).findFirst();
    }
}
