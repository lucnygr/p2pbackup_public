package at.lucny.p2pbackup.application.service;

import at.lucny.p2pbackup.backup.service.BackupService;
import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.configuration.support.RecoveryState;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Component
public class BackupAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupAgent.class);

    private final BackupService backupService;

    private final Configuration configuration;

    public BackupAgent(BackupService backupService, Configuration configuration) {
        this.backupService = backupService;
        this.configuration = configuration;
    }

    @Async
    public Future<Void> backup() {
        if (!this.configuration.containsKey(ConfigurationConstants.PROPERTY_RECOVERY_STATE)) {
            LOGGER.info("backup all root-directories");
            this.backupService.backup();
            LOGGER.info("finished to backup all root-directories");
        } else {
            LOGGER.info("cannot backup because there is an ongoing recovery in state {}", this.configuration.get(RecoveryState.class, ConfigurationConstants.PROPERTY_RECOVERY_STATE));
        }

        return CompletableFuture.completedFuture(null);
    }
}
