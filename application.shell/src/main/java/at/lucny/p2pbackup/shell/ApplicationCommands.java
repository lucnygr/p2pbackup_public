package at.lucny.p2pbackup.shell;

import at.lucny.p2pbackup.application.service.BackupAgent;
import at.lucny.p2pbackup.application.service.CloudUploadAgent;
import at.lucny.p2pbackup.application.service.DistributionAgent;
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

    private final DistributionAgent distributionAgent;

    private final BackupAgent backupAgent;

    private final CloudUploadAgent cloudUploadAgent;

    private final List<AsyncTaskExecutor> asyncTaskExecutors;

    public ApplicationCommands(ApplicationEventPublisher publisher, DistributionAgent distributionAgent, BackupAgent backupAgent, CloudUploadAgent cloudUploadAgent, List<AsyncTaskExecutor> asyncTaskExecutors) {
        this.publisher = publisher;
        this.distributionAgent = distributionAgent;
        this.backupAgent = backupAgent;
        this.cloudUploadAgent = cloudUploadAgent;
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

    @ShellMethod("uploads blocks to the cloud and to other peers")
    public void uploadBlocks() {
        this.cloudUploadAgent.upload();
        this.distributionAgent.distribute();
    }

}
