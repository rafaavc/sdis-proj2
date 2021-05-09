package channels;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;

public class MulticastChannel {
    private final InetAddress host;
    private final int port;
    private final String hostName;
    private final ChannelType type;
    private final MulticastSocket socket;

    public enum ChannelType {
        CONTROL,
        BACKUP,
        RESTORE
    }

    public static final HashMap<ChannelType, String> messages = new HashMap<>();

    static {
        messages.put(ChannelType.CONTROL, "MC");
        messages.put(ChannelType.BACKUP, "MDB");
        messages.put(ChannelType.RESTORE, "MDR");
    }

    public MulticastChannel(ChannelType type, String host, int port) throws IOException {
        this.host = InetAddress.getByName(host);
        this.hostName = host;
        this.port = port;
        this.type = type;
        this.socket = new MulticastSocket(this.getPort());
    }

    public void send(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length,  this.host, this.port);

        this.socket.send(packet);
    }

    public void send(String data) throws IOException {
        this.send(data.getBytes());
    }

    public void close() {
        this.socket.close();
    }

    public InetAddress getHost() {
        return host;
    }

    public ChannelType getType() {
        return type;
    }

    public int getPort() {
        return port;
    }

    public MulticastSocket getSocket() {
        return socket;
    }

    @Override
    public String toString() {
        return type + "(" + this.hostName + ":" + this.port + ")";
    }
}
