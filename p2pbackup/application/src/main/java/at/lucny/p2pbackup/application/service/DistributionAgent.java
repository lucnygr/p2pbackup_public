package at.lucny.p2pbackup.application.service;

import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.upload.service.DistributionService;
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
public class DistributionAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributionAgent.class);

    private final AsyncTaskExecutor taskExecutor;

    private final DistributionService distributionService;

    private final Configuration configuration;

    private Future<Void> runningTask;

    public DistributionAgent(Configuration configuration, TaskScheduler taskScheduler, AsyncTaskExecutor taskExecutor, DistributionService distributionService) {
        this.taskExecutor = taskExecutor;
        this.distributionService = distributionService;
        this.configuration = configuration;

        if (!configuration.containsKey(ConfigurationConstants.PROPERTY_DISABLE_DISTRIBUTION_AGENT) || !configuration.getBoolean(ConfigurationConstants.PROPERTY_DISABLE_DISTRIBUTION_AGENT)) {
            taskScheduler.scheduleWithFixedDelay(this::distribute, 1000L * 60 * 5);
        }
    }

    public synchronized Future<Void> distribute() {
        if (this.runningTask == null && !this.configuration.containsKey(ConfigurationConstants.PROPERTY_RECOVERY_STATE)) {
            this.runningTask = this.taskExecutor.submit(() -> {
                try {
                    LOGGER.debug("distributing blocks");
                    this.distributionService.distributeBlocks();
                    LOGGER.debug("verify replica amount");
                    this.distributionService.verifyEnoughReplicas();
                    LOGGER.debug("finished distributing blocks");
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
