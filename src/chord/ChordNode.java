package chord;

import java.net.InetAddress;

public class ChordNode {
    private final InetAddress address;
    private final int id;

    public ChordNode (InetAddress a, int id) {
        this.address = a;
        this.id = id;
    }

    public InetAddress getInetAddress() {
        return address;
    }

    public int getId() {
        return id;
    }
}
