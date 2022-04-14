package at.lucny.p2pbackup.backup.service;

import at.lucny.p2pbackup.core.support.CryptoConstants;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

import static at.lucny.p2pbackup.backup.support.BackupConstants.ONE_KILOBYTE;


@Service
@Validated
public class FixedSizeChunkerServiceImpl implements ChunkerService {

    public static final int BLOCK_SIZE = ONE_KILOBYTE * 500;

    @Override
    public ChunkIterator createIterator(Path filePath) {
        return new ChunkIterator(filePath, BLOCK_SIZE, true, CryptoConstants.BLOCK_HASH_ALGORITHM);
    }

}
