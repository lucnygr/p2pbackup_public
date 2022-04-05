package at.lucny.p2pbackup.restore.service;

import at.lucny.p2pbackup.core.domain.RootDirectory;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.LocalDateTime;

public interface RestorationService {

    void beginRestore(@NotNull RootDirectory rootDirectory, @NotNull LocalDateTime forDate, @NotNull Path targetDir);

    void saveBlock(@NotNull String blockId, @NotNull ByteBuffer data);

    void restoreBlocks();
}
