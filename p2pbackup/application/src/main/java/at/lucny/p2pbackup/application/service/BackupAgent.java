package at.lucny.p2pbackup.application.service;

import at.lucny.p2pbackup.backup.service.BackupService;
import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.configuration.support.RecoveryState;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Component
@DependsOn("initConfigurationBean") // depending on the bean for property initialization
public class BackupAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupAgent.class);

    private final BackupService backupService;

    private final Configuration configuration;

    private final AsyncTaskExecutor taskExecutor;

    private Future<Void> runningTask;

    public BackupAgent(BackupService backupService, Configuration configuration, AsyncTaskExecutor taskExecutor) {
        this.backupService = backupService;
        this.configuration = configuration;
        this.taskExecutor = taskExecutor;
    }

    public Future<Void> backup() {
        if (this.configuration.containsKey(ConfigurationConstants.PROPERTY_RECOVERY_STATE)) {
            LOGGER.info("cannot backup because there is an ongoing recovery in state {}", this.configuration.get(RecoveryState.class, ConfigurationConstants.PROPERTY_RECOVERY_STATE));
            return CompletableFuture.completedFuture(null);
        }

        if (this.runningTask == null) {
            this.runningTask = this.taskExecutor.submit(() -> {
                try {
                    LOGGER.info("backup all root-directories");
                    this.backupService.backup();
                    LOGGER.info("finished to backup all root-directories");
                } catch (Exception e) {
                    LOGGER.warn("unable to backup blocks", e);
                } finally {
                    this.runningTask = null;
                }
                return null;
            });
        } else {
            LOGGER.info("backup already running");
        }
        return this.runningTask;
    }
}
