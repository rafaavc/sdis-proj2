package server.handlers;

import messages.Message;
import state.PeerState;

import java.net.InetAddress;

import configuration.PeerConfiguration;

public abstract class Handler {
    protected final PeerConfiguration configuration;
    protected final PeerState peerState;

    public Handler(PeerConfiguration configuration) {
        this.configuration = configuration;
        this.peerState = configuration.getPeerState();
    }

    public PeerConfiguration getConfiguration() {
        return configuration;
    }

    public abstract void execute(Message msg, InetAddress senderAddress);
}
