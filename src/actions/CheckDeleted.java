package actions;

import configuration.PeerConfiguration;
import configuration.ProtocolVersion;
import messages.MessageFactory;

public class CheckDeleted {
    private final PeerConfiguration configuration;

    public CheckDeleted(PeerConfiguration configuration) {
        this.configuration = configuration;
    }

    public void execute() {
        try {
            for (String fileId : configuration.getPeerState().getBackedUpFileIds())
            {
                byte[] msg = new MessageFactory(new ProtocolVersion(1, 1)).getFilecheckMessage(configuration.getPeerId(), fileId);
                configuration.getMC().send(msg);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
