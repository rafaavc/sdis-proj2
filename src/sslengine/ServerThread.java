package sslengine;

import utils.Logger;

public class ServerThread implements Runnable {

    private final SSLServer server;

    public ServerThread(SSLServer server){
        this.server = server;
    }

    public SSLServer getServer() {
        return server;
    }

    @Override
    public void run() {
        try {
            this.server.start();
        } catch (Exception e) {
            Logger.error("server running", e, true);
        }
    }
}
