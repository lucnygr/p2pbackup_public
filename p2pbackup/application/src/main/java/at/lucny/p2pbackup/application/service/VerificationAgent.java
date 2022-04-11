package at.lucny.p2pbackup.application.service;

import at.lucny.p2pbackup.configuration.support.ConfigurationConstants;
import at.lucny.p2pbackup.verification.service.VerificationService;
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
public class VerificationAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationAgent.class);

    private final VerificationService verificationService;

    private final AsyncTaskExecutor taskExecutor;

    private final Configuration configuration;

    private Future<Void> runningTask;

    public VerificationAgent(VerificationService verificationService, Configuration configuration, TaskScheduler taskScheduler, AsyncTaskExecutor taskExecutor) {
        this.verificationService = verificationService;
        this.configuration = configuration;
        this.taskExecutor = taskExecutor;

        if (!this.configuration.containsKey(ConfigurationConstants.PROPERTY_DISABLE_VERIFICATION_AGENT) || !this.configuration.getBoolean(ConfigurationConstants.PROPERTY_DISABLE_VERIFICATION_AGENT)) {
            taskScheduler.scheduleWithFixedDelay(this::verify, 1000L * 60 * 5);
        }
    }

    public synchronized Future<Void> verify() {
        if (this.runningTask == null && !this.configuration.containsKey(ConfigurationConstants.PROPERTY_RECOVERY_STATE)) {
            this.runningTask = this.taskExecutor.submit(() -> {
                try {
                    this.verificationService.verifyBlocks();
                } catch (Exception e) {
                    LOGGER.warn("unable to verify blocks", e);
                } finally {
                    this.runningTask = null;
                }
                return null;
            });
        }
        return this.runningTask;
    }
}
