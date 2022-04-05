package at.lucny.p2pbackup.application.service;

import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.restore.service.RestorationService;
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
public class RestoreAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreAgent.class);

    private final RestorationService restorationService;

    private final AsyncTaskExecutor taskExecutor;

    private Future<Void> runningTask;

    public RestoreAgent(RestorationService restorationService, TaskScheduler taskScheduler, AsyncTaskExecutor taskExecutor, Configuration configuration) {
        this.restorationService = restorationService;
        this.taskExecutor = taskExecutor;

        if (!configuration.containsKey(ConfigurationConstants.PROPERTY_DISABLE_RESTORATION_AGENT) || !configuration.getBoolean(ConfigurationConstants.PROPERTY_DISABLE_RESTORATION_AGENT)) {
            taskScheduler.scheduleWithFixedDelay(this::restore, 1000L * 60 * 5);
        }
    }

    public synchronized Future<Void> restore() {
        if (this.runningTask == null) {
            this.runningTask = this.taskExecutor.submit(() -> {
                try {
                    this.restorationService.restoreBlocks();
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
