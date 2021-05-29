package sslengine;

import java.nio.ByteBuffer;

public class ReadResult {
    private final int bytesRead;
    private final ByteBuffer data;

    public ReadResult(int bytesRead, ByteBuffer data) {
        this.bytesRead = bytesRead;
        this.data = data;
    }

    public int getBytesRead() {
        return bytesRead;
    }

    public ByteBuffer getData() {
        return data;
    }
    
}
