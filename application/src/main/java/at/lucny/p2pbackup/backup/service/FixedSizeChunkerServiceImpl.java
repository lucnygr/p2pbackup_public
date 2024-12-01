package at.lucny.p2pbackup.backup.service;

import at.lucny.p2pbackup.application.config.P2PBackupProperties;
import at.lucny.p2pbackup.core.support.CryptoConstants;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

import static at.lucny.p2pbackup.backup.support.BackupConstants.ONE_KILOBYTE;


@Service
@Validated
public class FixedSizeChunkerServiceImpl implements ChunkerService {

    private final int blockSize;

    public FixedSizeChunkerServiceImpl(P2PBackupProperties p2PBackupProperties) {
        long blockSize = p2PBackupProperties.getBlockSize().toBytes();
        if (blockSize < ONE_KILOBYTE * 100 || blockSize > ONE_KILOBYTE * ONE_KILOBYTE * 100) {
            throw new IllegalStateException("invalid property " + p2PBackupProperties.getBlockSize());
        }
        this.blockSize = (int) blockSize;
    }

    @Override
    public ChunkIterator createIterator(Path filePath) {
        return new ChunkIterator(filePath, this.blockSize, true, CryptoConstants.BLOCK_HASH_ALGORITHM);
    }

}
