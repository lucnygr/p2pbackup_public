package at.lucny.p2pbackup.restore.domain;

public enum RestoreType {

    /**
     * request a block to restore data.
     */
    RESTORE,

    /**
     * request a block to recover metadata. the datablock itself is not needed.
     */
    RECOVER,

    /**
     * recover a block because it contains metadata and restore the data.
     */
    RECOVER_META_DATA_AND_RESTORE_DATA;
}
