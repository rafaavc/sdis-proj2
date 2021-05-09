package channels.handlers.strategies;

import configuration.PeerConfiguration;
import messages.Message;

public abstract class RestoreStrategy {
    protected final PeerConfiguration configuration;

    public RestoreStrategy(PeerConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public abstract void sendChunk(Message msg) throws Exception;
}
