package at.lucny.p2pbackup.shell;

import at.lucny.p2pbackup.application.service.RestoreAgent;
import at.lucny.p2pbackup.backup.service.BackupService;
import at.lucny.p2pbackup.core.domain.RootDirectory;
import at.lucny.p2pbackup.restore.domain.RecoverBackupIndex;
import at.lucny.p2pbackup.restore.service.RecoveryService;
import at.lucny.p2pbackup.restore.service.RestorationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import javax.validation.constraints.NotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@ShellComponent
public class RestoreCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestoreCommands.class);

    private final BackupService backupService;

    private final RestorationService restorationService;

    private final RecoveryService recoveryService;

    private final RestoreAgent restoreAgent;

    public RestoreCommands(BackupService backupService, RestorationService restorationService, RecoveryService recoveryService, RestoreAgent restoreAgent) {
        this.backupService = backupService;
        this.restorationService = restorationService;
        this.recoveryService = recoveryService;
        this.restoreAgent = restoreAgent;
    }

    @ShellMethod("Restores the given directory to a specific directory for the current date")
    public void restoreDirectory(@NotNull String name, @NotNull String targetDirectory) {
        Path directory = Paths.get(targetDirectory);
        if (!Files.isDirectory(directory)) {
            LOGGER.info("target-directory {} is not a directory", targetDirectory);
            return;
        }

        Optional<RootDirectory> optionalRootDirectory = this.backupService.getRootDirectory(name);
        if (optionalRootDirectory.isEmpty()) {
            LOGGER.info("backup-directory {} is not configured", name);
        } else {
            RootDirectory rootDirectory = optionalRootDirectory.get();
            this.restorationService.beginRestore(rootDirectory, LocalDateTime.now(ZoneOffset.UTC), directory);
            this.restoreAgent.restore();
            LOGGER.info("started restore for directory {}", targetDirectory);
        }
    }

    @ShellMethod("Restores the given directory to a specific directory for the given date")
    public void restoreDirectoryAt(@NotNull String name, @NotNull String targetDirectory, @NotNull String date) {
        Optional<RootDirectory> optionalRootDirectory = this.backupService.getRootDirectory(name);

        Path directory = Paths.get(targetDirectory);
        if (!Files.isDirectory(directory)) {
            LOGGER.info("target-directory {} is not a directory", targetDirectory);
            return;
        }

        if (optionalRootDirectory.isEmpty()) {
            LOGGER.info("backup-directory {} is not configured", name);
            return;
        }

        LocalDateTime dateTime = LocalDateTime.parse(date);

        this.restorationService.beginRestore(optionalRootDirectory.get(), dateTime, directory);
        this.restoreAgent.restore();
        LOGGER.info("started restore for directory {}", targetDirectory);
    }

    @ShellMethod("Starts the recovery of the backup-index")
    public void recoverIndex() {
        this.recoveryService.recoverBackupIndex();
    }

    @ShellMethod("Shows all recovered backup indizes")
    public void listIndizes() {
        List<RecoverBackupIndex> indexList = this.recoveryService.getAllRestoreBackupIndizes();
        for (RecoverBackupIndex index : indexList) {
            LOGGER.info("ID: {}, Date: {}", index.getId(), index.getDate());
        }
    }

    @ShellMethod("Starts the recovery of the backup-index with the given id")
    public void startRecovery(@NotNull String backupIndexId) {
        this.recoveryService.startRecovery(backupIndexId);
        this.restoreAgent.restore();
    }

}
