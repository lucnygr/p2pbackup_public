package at.lucny.p2pbackup.core.support;

import javax.crypto.Mac;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MacOutputStream extends FilterOutputStream {

    private final Mac mac;

    public MacOutputStream(OutputStream outputStream, Mac mac) {
        super(outputStream);
        this.mac = mac;
    }

    public Mac getMac() {
        return mac;
    }

    @Override
    public void write(int b) throws IOException {
        this.out.write(b);
        mac.update((byte) b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.out.write(b, off, len);
        mac.update(b, off, len);
    }
}
