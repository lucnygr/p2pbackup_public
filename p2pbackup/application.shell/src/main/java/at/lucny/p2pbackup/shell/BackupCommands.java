package at.lucny.p2pbackup.shell;

import at.lucny.p2pbackup.backup.service.BackupService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import jakarta.validation.constraints.NotNull;

@ShellComponent
public class BackupCommands {

    private final BackupService backupService;

    public BackupCommands(BackupService backupService) {
        this.backupService = backupService;
    }

    @ShellMethod("Add a new path to be backed up by the backup solution")
    public void addRootDirectory(@NotNull String name, @NotNull String path) {
        this.backupService.addRootDirectory(name, path);
    }
}
