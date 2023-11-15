package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.core.domain.RootDirectory;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.LocalDateTime;

public interface RestoreManagementService {

    void beginRestore(@NotNull RootDirectory rootDirectory, @NotNull LocalDateTime forDate, @NotNull Path targetDir);
}
