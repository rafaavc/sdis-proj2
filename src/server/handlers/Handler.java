package server.handlers;


import configuration.PeerConfiguration;
import messages.Message;

public abstract class Handler {
    protected final PeerConfiguration configuration;

    public Handler(PeerConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Handles a message
     * @param msg The message to handle
     * @return The response to the message
     */
    public abstract Message handle(Message msg) throws Exception;
}
