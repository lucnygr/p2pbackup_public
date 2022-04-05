package at.lucny.p2pbackup.backup.service;

import at.lucny.p2pbackup.backup.dto.Block;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;
import java.util.Iterator;

public interface ChunkerService {

    /**
     * Chunks the file from the given path in blocks.
     *
     * @param filePath
     * @return an iterator that delivers all blocks from the file one-by-one
     */
    @NotNull Iterator<Block> createIterator(@NotNull Path filePath);
}
