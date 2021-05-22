package server.handlers.strategies;

import configuration.PeerConfiguration;
import configuration.ProtocolVersion;
import files.FileManager;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import utils.Logger;

public class VanillaRestoreStrategy extends RestoreStrategy {
    public VanillaRestoreStrategy(PeerConfiguration configuration) {
        super(configuration);
    }

    public void sendChunk(Message msg) throws Exception {
        // if (version != 1.0)
        // // else
        // byte[] chunkData = new FileManager(configuration.getRootDir()).readChunk(msg.getFileId(), msg.getChunkNo());
        // byte[] chunkMsg = new MessageFactory(new ProtocolVersion(1, 0)).getChunkMessage(this.configuration.getPeerId(), msg.getFileId(), msg.getChunkNo(), chunkData);
        
        // //this.configuration.getMDR().send(chunkMsg);
        // SSLClient client = new SSLClient(configuration.getServer().getAddress(), configuration.getServer().getPort());
        // client.connect();
        // client.write(chunkMsg);
        // client.read();
        // client.shutdown();
        //Logger.todo(this);
    }
}
