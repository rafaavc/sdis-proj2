package actions;

import java.io.IOException;
import java.net.InetSocketAddress;

import sslengine.SSLClient;
import utils.Logger;

public class Action {
    protected final SSLClient client;
    protected final InetSocketAddress destination;

    public Action(InetSocketAddress destination) throws Exception {
        this.destination = destination;
        this.client = new SSLClient(destination.getAddress().toString(), destination.getPort());
        this.client.connect();
    }

    public Action(InetSocketAddress destination, SSLClient client) {
        this.destination = destination;
        this.client = client;
    }

    protected void shutdown() {
        try {
            this.client.shutdown();
        } catch (IOException e) {
            Logger.error(e, true);
        }
    }
}
