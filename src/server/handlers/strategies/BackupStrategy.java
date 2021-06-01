package server.handlers.strategies;

import configuration.PeerConfiguration;
import messages.Message;
import messages.MessageFactory;
import messages.trackers.StoredTracker;

public abstract class BackupStrategy {
    protected final PeerConfiguration configuration;

    public BackupStrategy(PeerConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public abstract void backup(StoredTracker storedTracker, Message message) throws Exception;
    public abstract void sendAlreadyHadStored(Message message) throws Exception;
}
