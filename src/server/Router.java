package server;

import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;

public interface Router {
    public void handle(byte[] dataReceived, int length, SocketChannel socket, SSLEngine engine) throws Exception;
}
