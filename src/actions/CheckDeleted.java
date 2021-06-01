package actions;

import configuration.PeerConfiguration;
import sslengine.SSLClient;

public class CheckDeleted {
    private final PeerConfiguration configuration;

    public CheckDeleted(PeerConfiguration configuration) {
        this.configuration = configuration;
    }

    public void execute() {
        try {
            SSLClient client = new SSLClient(configuration.getServer().getAddress(), configuration.getServer().getPort());
            client.connect();
            for (String fileId : configuration.getPeerState().getBackedUpFileIds())
            {
                // byte[] msg = new MessageFactory(new ProtocolVersion(1, 1)).getFilecheckMessage(configuration.getPeerId(), fileId);
                // client.write(msg);
                // client.read();
                //configuration.getMC().send(msg);
                //Logger.todo(this);

            }
            client.shutdown();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
