package at.lucny.p2pbackup.backup.support;

import java.security.SecureRandom;
import java.util.Random;

public final class BackupUtils {

    private BackupUtils() {
    }

    public static final Random RANDOM = new SecureRandom();
}
