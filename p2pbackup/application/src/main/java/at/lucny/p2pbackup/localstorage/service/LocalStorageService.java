package at.lucny.p2pbackup.localstorage.service;

import at.lucny.p2pbackup.localstorage.dto.LocalStorageEntry;
import at.lucny.p2pbackup.network.dto.BackupBlock;
import at.lucny.p2pbackup.network.dto.BackupBlockFailure;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface LocalStorageService {

    void initializeDirectories() throws IOException;

    @NotNull LocalStorageEntry saveInLocalStorage(@NotNull String id, @NotNull ByteBuffer block);

    /**
     * Deletes the block with the given ID from the local storage. Idempotent.
     *
     * @param id the id of the block
     * @return true if the file was successfully deleted. returns also true when the file was not present. returns anly false in case of an error.
     */
    boolean removeFromLocalStorage(@NotNull String id);

    @NotNull Optional<BackupBlockFailure.BackupBlockFailureType> saveFromUserInLocalStorage(@NotNull String userId, @NotNull BackupBlock backupBlock);

    @NotNull Optional<Path> loadFromLocalStorage(@NotNull String id);

    @NotNull Optional<Path> loadFromLocalStorage(@NotNull String userId, @NotNull String id);

    void removeFromLocalStorage(@NotNull String userId, @NotNull @NotEmpty List<String> blockIds);

    /**
     * Loads all blocks with the given prefix from the given user
     *
     * @param userId the user
     * @param prefix prefix of the searched blocks
     * @return a list of paths to all blocks that start with the prefix
     */
    @NotNull List<Path> loadFromLocalStorageByPrefix(@NotNull String userId, @NotNull String prefix);

    @NotNull List<String> getBlockIds(@NotNull String userId);
}
