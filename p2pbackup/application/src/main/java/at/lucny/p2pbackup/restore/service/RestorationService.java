package at.lucny.p2pbackup.restore.service;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;

public interface RestorationService {

    void saveBlock(@NotNull String blockId, @NotNull ByteBuffer data);

    void restoreBlocks();
}
