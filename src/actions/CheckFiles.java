package actions;

import configuration.PeerConfiguration;
import messages.MessageFactory;
import sslengine.SSLClient;
import utils.Logger;

public class CheckFiles implements Action {
    private final PeerConfiguration configuration;

    public CheckFiles(PeerConfiguration configuration) {
        this.configuration = configuration;
    }

    public void execute() {
        try {
            Logger.debug(Logger.DebugType.CHECK, "Checking files.");
            SSLClient.sendQueued(configuration, configuration.getChord().getSuccessor().getInetSocketAddress(),
                    MessageFactory.getCheckMessage(configuration.getPeerId(), configuration.getChord().getSelf()), false);
        } catch(Exception e) {
            Logger.error("checking files", e, true);
        }
    }
}
