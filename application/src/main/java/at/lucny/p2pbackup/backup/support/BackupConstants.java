package at.lucny.p2pbackup.backup.support;

public final class BackupConstants {

    private BackupConstants() {
    }

    public static final int ONE_KILOBYTE = 1024;

    /**
     * Prefix for blocks that contain an index of all version-blocks of a backup.
     */
    public static final String BACKUP_INDEX_BLOCK_PREFIX = "IDX_";
}
