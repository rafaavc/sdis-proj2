package utils;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class IPFinder {
    public static InetAddress find() throws UnknownHostException {
        InetAddress localIP;
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            localIP = socket.getLocalAddress();
            socket.close();
        } catch (Throwable e) {
            localIP = InetAddress.getLocalHost();
            if (socket != null) socket.close();
        }
        return localIP;
    }
}
