package at.lucny.p2pbackup.application.service;

import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.upload.service.CloudUploadService;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.concurrent.Future;

@Component
@DependsOn("initConfigurationBean") // depending on the bean for property initialization
public class CloudUploadAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudUploadAgent.class);

    private final CloudUploadService cloudUploadService;

    private final AsyncTaskExecutor taskExecutor;

    private Future<Void> runningTask;

    public CloudUploadAgent(CloudUploadService cloudUploadService, Configuration configuration, TaskScheduler taskScheduler, AsyncTaskExecutor taskExecutor) {
        this.cloudUploadService = cloudUploadService;
        this.taskExecutor = taskExecutor;

        if (!configuration.containsKey(ConfigurationConstants.PROPERTY_DISABLE_UPLOAD_AGENT) || !configuration.getBoolean(ConfigurationConstants.PROPERTY_DISABLE_UPLOAD_AGENT)) {
            taskScheduler.scheduleWithFixedDelay(this::upload, 1000L * 60);
        }
    }

    public synchronized Future<Void> upload() {
        if (this.runningTask == null) {
            this.runningTask = this.taskExecutor.submit(() -> {
                try {
                    LOGGER.info("upload all local blocks");
                    this.cloudUploadService.uploadLocalBackupBlocks();
                    LOGGER.info("finished uploading all local blocks");
                } catch (Exception e) {
                    LOGGER.warn("unable to restore blocks", e);
                } finally {
                    this.runningTask = null;
                }
                return null;
            });
        }
        return this.runningTask;
    }
}
