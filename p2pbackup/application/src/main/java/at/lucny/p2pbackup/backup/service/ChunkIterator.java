package at.lucny.p2pbackup.backup.service;

import at.lucny.p2pbackup.backup.dto.Block;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ChunkIterator implements Iterator<Block> {

    private final Path filePath;

    private final boolean useDirectBuffer;

    private final int chunkSize;

    private final ReadableByteChannel channel;

    private final MessageDigest digest;

    private Block nextBlock;

    private ByteBuffer previousByteBuffer;

    protected ChunkIterator(Path filePath, int chunkSize, boolean useDirectBuffer, String messageDigestAlgorithm) {
        this.filePath = filePath;
        this.useDirectBuffer = useDirectBuffer;

        this.digest = DigestUtils.getDigest(messageDigestAlgorithm);
        this.chunkSize = chunkSize;
        try {
            InputStream fileInputStream = Files.newInputStream(filePath, StandardOpenOption.READ);
            DigestInputStream digestInputStream = new DigestInputStream(fileInputStream, this.digest);
            this.channel = Channels.newChannel(digestInputStream);
        } catch (IOException e) {
            throw new IllegalArgumentException("could not create input-stream for path " + filePath, e);
        }
    }

    private ByteBuffer newByteBuffer() {
        if (this.useDirectBuffer) {
            return ByteBuffer.allocateDirect(this.chunkSize);
        } else {
            return ByteBuffer.allocate(this.chunkSize);
        }
    }

    @Override
    public boolean hasNext() {
        if (this.nextBlock == null) {
            return this.read() > 0;
        }
        return true;
    }

    private int read() {
        try {
            ByteBuffer buffer;

            // TODO does this reuse really work?
            if (this.previousByteBuffer != null && !this.previousByteBuffer.hasRemaining()) {
                this.previousByteBuffer.clear();
                buffer = this.previousByteBuffer;
                this.previousByteBuffer = null;
            } else {
                buffer = this.newByteBuffer();
            }

            int nrOfBytes = this.channel.read(buffer);
            if (nrOfBytes > 0) {
                buffer.flip();
                byte[] hash = this.digest.digest();
                this.nextBlock = new Block(buffer, Base64.getEncoder().encodeToString(hash));
                this.digest.reset();
            } else {
                this.channel.close();
            }
            return nrOfBytes;
        } catch (IOException e) {
            throw new IllegalStateException("unable to chunk file " + this.filePath, e);
        }
    }

    @Override
    public Block next() {
        if (this.nextBlock == null) {
            if (this.read() <= 0) {
                throw new NoSuchElementException("no more chunks");
            }
        }
        Block block = this.nextBlock;
        this.nextBlock = null;
        this.previousByteBuffer = block.content();
        return block;
    }
}
