package at.lucny.p2pbackup.backup.service;

import at.lucny.p2pbackup.core.support.CryptoConstants;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;


@Service
@Validated
public class FixedSizeChunkerServiceImpl implements ChunkerService {

    private static final int CHUNK_SIZE = 1024 * 100;

    @Override
    public ChunkIterator createIterator(Path filePath) {
        return new ChunkIterator(filePath, CHUNK_SIZE, true, CryptoConstants.BLOCK_HASH_ALGORITHM);
    }

}
