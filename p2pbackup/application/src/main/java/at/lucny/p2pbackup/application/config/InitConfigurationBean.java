package at.lucny.p2pbackup.application.config;

import at.lucny.p2pbackup.backup.service.BackupService;
import at.lucny.p2pbackup.cloud.CloudStorageService;
import at.lucny.p2pbackup.cloud.CloudStorageServiceProvider;
import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.localstorage.service.LocalStorageService;
import at.lucny.p2pbackup.user.service.UserService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Component
public class InitConfigurationBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitConfigurationBean.class);

    private final P2PBackupProperties p2PBackupProperties;

    private final Configuration configuration;

    private final UserService userService;

    private final BackupService backupService;

    private final CloudStorageServiceProvider cloudStorageServiceProvider;

    private final LocalStorageService localStorageService;

    public InitConfigurationBean(P2PBackupProperties p2PBackupProperties, Configuration configuration, UserService userService, BackupService backupService, CloudStorageServiceProvider cloudStorageServiceProvider, LocalStorageService localStorageService) {
        this.p2PBackupProperties = p2PBackupProperties;
        this.configuration = configuration;
        this.userService = userService;
        this.backupService = backupService;
        this.cloudStorageServiceProvider = cloudStorageServiceProvider;
        this.localStorageService = localStorageService;
    }

    @PostConstruct
    public void postConstruct() throws IOException {
        if (this.p2PBackupProperties.getOldKeystore() != null) {
            LOGGER.info("found path to old keystore {}, disable upload-, distribution- and verification-agent", this.p2PBackupProperties.getOldKeystore());
            this.configuration.setProperty(ConfigurationConstants.PROPERTY_DISABLE_UPLOAD_AGENT, true);
            this.configuration.setProperty(ConfigurationConstants.PROPERTY_DISABLE_DISTRIBUTION_AGENT, true);
            this.configuration.setProperty(ConfigurationConstants.PROPERTY_DISABLE_VERIFICATION_AGENT, true);
        }

        String firstStartup = this.configuration.getString(ConfigurationConstants.PROPERTY_FIRST_STARTUP);
        if (firstStartup != null) {
            LOGGER.info("first startup was on {}, ignore init config", firstStartup);
            return;
        }

        this.configuration.setProperty(ConfigurationConstants.PROPERTY_FIRST_STARTUP, Instant.now().toString());
        if (this.p2PBackupProperties.getInit() != null) {
            if (CollectionUtils.isNotEmpty(this.p2PBackupProperties.getInit().getUsers())) {
                for (UserProperties userProperties : this.p2PBackupProperties.getInit().getUsers()) {
                    LOGGER.info("initializing user {}", userProperties);
                    this.userService.addUser(userProperties.getUser(), userProperties.getAddress(), userProperties.getPort(), userProperties.getCertificatePath(), userProperties.getAllowBackupFromUser(), userProperties.getAllowBackupToUser(), false);
                }
                this.localStorageService.initializeDirectories();
            }
            this.initCloudProvider();
            this.initRootDirectories();
            this.configuration.setProperty(ConfigurationConstants.PROPERTY_DISABLE_UPLOAD_AGENT, this.p2PBackupProperties.getInit().getDisableUploadAgent());
            this.configuration.setProperty(ConfigurationConstants.PROPERTY_DISABLE_DISTRIBUTION_AGENT, this.p2PBackupProperties.getInit().getDisableDistributionAgent());
            this.configuration.setProperty(ConfigurationConstants.PROPERTY_DISABLE_VERIFICATION_AGENT, this.p2PBackupProperties.getInit().getDisableVerificationAgent());
            this.configuration.setProperty(ConfigurationConstants.PROPERTY_DISABLE_RESTORATION_AGENT, this.p2PBackupProperties.getInit().getDisableRestorationAgent());
        }
    }

    private void initCloudProvider() {
        if (CollectionUtils.isNotEmpty(this.p2PBackupProperties.getInit().getCloudProvider())) {
            for (CloudProviderProperties cloudProviderProperties : this.p2PBackupProperties.getInit().getCloudProvider()) {
                Optional<CloudStorageService> cloudService = this.cloudStorageServiceProvider.getCloudStorageServices().stream()
                        .filter(provider -> provider.getId().equals(cloudProviderProperties.getId())).findFirst();
                LOGGER.info("initializing cloud-service {}", cloudProviderProperties.getId());
                if (cloudService.isPresent()) {
                    cloudService.get().configure(cloudProviderProperties.getProperties());
                } else {
                    LOGGER.warn("configured cloud-provider {} not found", cloudProviderProperties.getId());
                }
            }
        }
    }

    private void initRootDirectories() {
        if (CollectionUtils.isNotEmpty(this.p2PBackupProperties.getInit().getRootDirectories())) {
            for (RootDirectoryProperties rootDirectory : this.p2PBackupProperties.getInit().getRootDirectories()) {
                LOGGER.info("initializing root-directory {}", rootDirectory);
                this.backupService.addRootDirectory(rootDirectory.getName(), rootDirectory.getPath().toString());
            }
        }
    }
}
