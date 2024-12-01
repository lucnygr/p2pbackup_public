package at.lucny.p2pbackup.core.service;

import at.lucny.p2pbackup.backup.support.BackupConstants;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.nio.ByteBuffer;

@Service
@Validated
public class ByteBufferPoolServiceImpl implements ByteBufferPoolService {

    private final KeyedObjectPool<Integer, ByteBuffer> pool;

    public ByteBufferPoolServiceImpl() {
        this.pool = new GenericKeyedObjectPool<>(new BaseKeyedPooledObjectFactory<>() {
            @Override
            public ByteBuffer create(Integer key) throws Exception {
                return ByteBuffer.allocateDirect(key * BackupConstants.ONE_KILOBYTE);
            }

            @Override
            public PooledObject<ByteBuffer> wrap(ByteBuffer value) {
                return new DefaultPooledObject<>(value);
            }

            @Override
            public void passivateObject(Integer key, PooledObject<ByteBuffer> p) throws Exception {
                p.getObject().clear();
                super.passivateObject(key, p);
            }
        });
    }

    private int getNextPowerOf2(int x) {
        if (x < 0)
            return 0;
        --x;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x + 1;
    }

    @Override
    public Integer calculateBufferSize(int minimumSize) {
        int neededSizeInByte = minimumSize * 10;
        double exactNeededSizeInKb = neededSizeInByte / 1024d;
        int neededSizeInKb = Math.max((int) Math.ceil(exactNeededSizeInKb), 8);
        return this.getNextPowerOf2(neededSizeInKb);
    }

    @Override
    public ByteBuffer borrowObject(Integer key) {
        try {
            return this.pool.borrowObject(key);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void returnObject(Integer key, ByteBuffer buffer) {
        try {
            this.pool.returnObject(key, buffer);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }


}
