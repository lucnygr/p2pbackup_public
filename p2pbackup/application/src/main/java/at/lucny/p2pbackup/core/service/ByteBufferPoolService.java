package at.lucny.p2pbackup.core.service;

import javax.validation.constraints.NotNull;
import java.nio.ByteBuffer;

public interface ByteBufferPoolService {

    @NotNull Integer calculateBufferSize(int minimumSize);

    @NotNull ByteBuffer borrowObject(@NotNull Integer key);

    void returnObject(@NotNull Integer key, @NotNull ByteBuffer buffer);
}
