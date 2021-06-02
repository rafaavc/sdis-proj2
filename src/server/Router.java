package server;

import messages.Message;

import javax.net.ssl.SSLEngine;
import java.nio.channels.SocketChannel;

public interface Router {
    void handle(Message message, SocketChannel socket, SSLEngine engine, String address) throws Exception;
}
