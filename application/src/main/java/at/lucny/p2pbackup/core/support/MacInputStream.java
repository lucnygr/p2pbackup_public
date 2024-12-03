package at.lucny.p2pbackup.core.support;

import javax.crypto.Mac;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MacInputStream extends FilterInputStream {

    private final Mac mac;

    public MacInputStream(InputStream inputStream, Mac mac) {
        super(inputStream);
        this.mac = mac;
    }

    public Mac getMac() {
        return mac;
    }

    @Override
    public int read() throws IOException {
        int result = this.in.read();
        if (result != -1) {
            this.mac.update((byte) result);
        }
        return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = this.in.read(b, off, len);
        if (result != -1) {
            this.mac.update(b, off, result);
        }
        return result;
    }
}
