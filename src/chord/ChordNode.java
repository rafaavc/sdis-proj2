package chord;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class ChordNode {
    private final InetSocketAddress address;
    private final int id;

    public ChordNode (InetSocketAddress a, int id) {
        this.address = a;
        this.id = id;
    }

    public InetSocketAddress getInetSocketAddress() {
        return address;
    }

    public InetAddress getInetAddress() {
        return address.getAddress();
    }

    public int getPort() {
        return address.getPort();
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "NODE[id=" + id + "]@" + address.getAddress().getHostAddress() + ":" + address.getPort();
    }
}
