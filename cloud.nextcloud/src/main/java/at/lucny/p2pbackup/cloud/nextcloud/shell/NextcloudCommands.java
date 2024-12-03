package at.lucny.p2pbackup.cloud.nextcloud.shell;

import at.lucny.p2pbackup.cloud.nextcloud.service.NextcloudStorageServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.shell.boot.SpringShellAutoConfiguration;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import jakarta.validation.constraints.NotNull;

@ConditionalOnBean(SpringShellAutoConfiguration.class)
@ShellComponent
public class NextcloudCommands {

    private final NextcloudStorageServiceImpl nextcloudStorageService;

    public NextcloudCommands(NextcloudStorageServiceImpl nextcloudStorageService) {
        this.nextcloudStorageService = nextcloudStorageService;
    }

    @ShellMethod("Configures the parameters for the nextcloud-instance")
    public void configureNextcloud(@NotNull String serviceUrl, @NotNull String username, @NotNull String password) {
        this.nextcloudStorageService.configure(serviceUrl, username, password);
    }

}
