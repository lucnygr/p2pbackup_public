package at.lucny.p2pbackup.localstorage.service;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;

public interface RestorationStorageService {

    @NotNull Optional<Path> saveInLocalStorage(@NotNull String id, @NotNull ByteBuffer block);

    @NotNull Optional<Path> loadFromLocalStorage(@NotNull String id);

    /**
     * Deletes all files from the restore-storage.
     * @return true if all files could be deleted, otherwise false
     */
    boolean deleteAll();
}
