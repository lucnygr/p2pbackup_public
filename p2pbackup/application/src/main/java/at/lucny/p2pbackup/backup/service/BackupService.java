package at.lucny.p2pbackup.backup.service;

import at.lucny.p2pbackup.core.domain.RootDirectory;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BackupService {

    @NotNull Optional<RootDirectory> addRootDirectory(@NotNull String name, @NotNull String path);

    @NotNull Set<String> backupRootDirectory(@NotNull RootDirectory rootDirectory);

    @NotNull List<RootDirectory> getRootDirectories();

    /**
     * Finds the root-directory by its logical name.
     *
     * @param name the name of the root-directory.
     * @return the {@link RootDirectory}-entity, otherwise an empty optional
     */
    @NotNull Optional<RootDirectory> getRootDirectory(@NotNull String name);

    void backup();

}
