package at.lucny.p2pbackup.shell;

import at.lucny.p2pbackup.application.service.*;
import at.lucny.p2pbackup.application.support.StopApplicationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.shell.ExitRequest;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.commands.Quit;

import java.util.List;

@ShellComponent
public class ApplicationCommands implements Quit.Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationCommands.class);

    private final ApplicationEventPublisher publisher;

    private final VerificationAgent verificationAgent;

    private final DistributionAgent distributionAgent;

    private final BackupAgent backupAgent;

    private final CloudUploadAgent cloudUploadAgent;

    private final RestoreAgent restoreAgent;

    private final List<AsyncTaskExecutor> asyncTaskExecutors;

    public ApplicationCommands(ApplicationEventPublisher publisher, VerificationAgent verificationAgent, DistributionAgent distributionAgent, BackupAgent backupAgent, CloudUploadAgent cloudUploadAgent, RestoreAgent restoreAgent, List<AsyncTaskExecutor> asyncTaskExecutors) {
        this.publisher = publisher;
        this.verificationAgent = verificationAgent;
        this.distributionAgent = distributionAgent;
        this.backupAgent = backupAgent;
        this.cloudUploadAgent = cloudUploadAgent;
        this.restoreAgent = restoreAgent;
        this.asyncTaskExecutors = asyncTaskExecutors;
    }

    @ShellMethod(value = "quit the application", key = {"exit", "quit", "stop"})
    public void exit() throws Exception {
        LOGGER.info("quitting application");
        this.publisher.publishEvent(new StopApplicationEvent(this));
        for (AsyncTaskExecutor taskExecutor : this.asyncTaskExecutors) {
            if (taskExecutor instanceof DisposableBean disposableBean) {
                disposableBean.destroy();
            }
        }
        throw new ExitRequest();
    }

    @ShellMethod(value = "verifies the necessary remote blocks")
    public void verifyBlocks() {
        this.verificationAgent.verify();
    }

    @ShellMethod("checks if new blocks need to be distributed")
    public void distributeBlocks() {
        this.distributionAgent.distribute();
    }

    @ShellMethod("Checks all configured directories for changes and backs up new content")
    public void backup() {
        this.backupAgent.backup();
        this.cloudUploadAgent.upload();
        this.distributionAgent.distribute();
    }

    @ShellMethod("uploads blocks to the cloud")
    public void uploadBlocks() {
        this.cloudUploadAgent.upload();
        this.distributionAgent.distribute();
    }

    @ShellMethod("restores blocks from other users")
    public void restoreBlocks() {
        this.restoreAgent.restore();
    }

}
