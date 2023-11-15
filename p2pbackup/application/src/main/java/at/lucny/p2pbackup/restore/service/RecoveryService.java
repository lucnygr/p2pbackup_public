package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.restore.domain.RecoverBackupIndex;

import jakarta.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

public interface RecoveryService {

    void initializeRecoveryMode();

    /**
     * Returns true if there is currently an active recovery running.
     *
     * @return if recovery is active
     */
    boolean isRecoveryActive();

    void requestBackupIndex();

    /**
     * Creates for each given block-id a {@link at.lucny.p2pbackup.core.domain.BlockMetaData}-entity and sets the given user-id as {@link at.lucny.p2pbackup.core.domain.DataLocation}.
     * The hash-property of each {@link at.lucny.p2pbackup.core.domain.BlockMetaData} is set to null.
     *
     * @param userId   the id of the user that stores the blocks
     * @param blockIds all blocks stored by the user
     */
    void recoverBlockMetaData(@NotNull String userId, @NotNull Set<String> blockIds);

    void recoverBackupIndex(String userId, @NotNull ByteBuffer backupIndexByteBuffer);

    /**
     * Creates restore-tasks for all version-blocks of the given backup-index
     *
     * @param idOfBackupIndex the id of the backup index
     */
    void startRecovery(@NotNull String idOfBackupIndex);

    @NotNull List<RecoverBackupIndex> getAllRestoreBackupIndizes();

    /**
     * recovers the metadata from the given block and returns true if its a data-block
     *
     * @param userId  the user-id that saved the block
     * @param blockId the id of the block
     * @param data    the data of the block as byte-buffer
     * @return true if the recovered block was a data-block
     */
    boolean recoverMetaData(String userId, @NotNull String blockId, @NotNull ByteBuffer data);

}
