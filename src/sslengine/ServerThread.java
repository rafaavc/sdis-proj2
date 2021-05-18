package sslengine;

public class ServerThread implements Runnable {

    private SSLServer server;

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
            e.printStackTrace();
        }
    }
}
