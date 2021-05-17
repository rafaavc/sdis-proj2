package chord;

import java.net.InetAddress;

public class ChordNode {
    private final InetAddress address;
    private final int port;
    private final int id;

    public ChordNode (InetAddress a, int port, int id) {
        this.address = a;
        this.id = id;
        this.port = port;
    }

    public InetAddress getInetAddress() {
        return address;
    }

    public int getId() {
        return id;
    }

    public int getPort() {
        return port;
    }
}
