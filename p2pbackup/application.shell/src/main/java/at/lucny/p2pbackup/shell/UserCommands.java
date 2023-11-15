package at.lucny.p2pbackup.shell;

import at.lucny.p2pbackup.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;

@ShellComponent
public class UserCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserCommands.class);

    private final UserService userService;

    public UserCommands(UserService userService) {
        this.userService = userService;
    }

    @ShellMethod("Add a new user to the backup-solution")
    public void addUser(@NotNull String userId, @NotNull String host, @NotNull @Min(0) Integer port, @NotNull String pathToCertificate, @NotNull Boolean allowBackupDataFromUser, @NotNull Boolean allowBackupDataToUser) {
        Path certificatePath = Paths.get(pathToCertificate);
        if (!certificatePath.toFile().exists()) {
            LOGGER.info("Certificate {} does not exist", pathToCertificate);
        }

        this.userService.addUser(userId, host, port, certificatePath, allowBackupDataFromUser, allowBackupDataToUser, true);
    }

    @ShellMethod("Delete the user with the given userId")
    public void deleteUser(@NotNull String userId) {
        this.userService.deleteUser(userId);
    }

    @ShellMethod("Changes the certificate of the given user")
    public void changeCertificate(@NotNull String userId, @NotNull String pathToCertificate) {
        Path certificatePath = Paths.get(pathToCertificate);
        if (!certificatePath.toFile().exists()) {
            LOGGER.info("Certificate {} does not exist", pathToCertificate);
        }

        this.userService.changeCertificate(userId, certificatePath);
    }

    @ShellMethod("Changes the permissions of the user")
    public void changePermissions(@NotNull String userId, @NotNull Boolean allowBackupDataFromUser, @NotNull Boolean allowBackupDataToUser) {
        this.userService.changePermissions(userId, allowBackupDataFromUser, allowBackupDataToUser);
    }

}
