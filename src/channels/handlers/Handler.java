package channels.handlers;

import messages.Message;
import state.PeerState;

import java.net.InetAddress;

import channels.MulticastChannel.ChannelType;
import configuration.PeerConfiguration;
import exceptions.ArgsException;

public abstract class Handler {
    protected final PeerConfiguration configuration;
    protected final PeerState peerState;

    public Handler(PeerConfiguration configuration) {
        this.configuration = configuration;
        this.peerState = configuration.getPeerState();
    }

    public static Handler get(PeerConfiguration configuration, ChannelType type) throws ArgsException {
        switch (type) {
            case CONTROL:
                return new ControlChannelHandler(configuration, configuration.getRestoreStrategy());
            case BACKUP:
                return new BackupChannelHandler(configuration, configuration.getBackupStrategy());
            case RESTORE:
                return new RestoreChannelHandler(configuration);
        }
        return null;
    }

    public PeerConfiguration getConfiguration() {
        return configuration;
    }

    public abstract void execute(Message msg, InetAddress senderAddress);
}
