package server;

import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;

import messages.Message;

public interface Router {
    public void handle(Message message, SocketChannel socket, SSLEngine engine, String address) throws Exception;
}
