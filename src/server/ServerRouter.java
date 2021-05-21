package server;

import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;

import configuration.PeerConfiguration;
import utils.Logger;

public class ServerRouter implements Router {
    
    private final PeerConfiguration configuration;

    public ServerRouter(PeerConfiguration configuration) {
        this.configuration = configuration;
    }

    public void handle(byte[] dataReceived, SocketChannel socket, SSLEngine engine) throws Exception {
        Logger.log("Received from client with address " + socket.getRemoteAddress().toString() + ":\n" + new String(dataReceived));
        configuration.getServer().write(socket, engine, "I am your server".getBytes());
    }
}
