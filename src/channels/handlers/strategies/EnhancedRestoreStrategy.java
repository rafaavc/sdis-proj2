package channels.handlers.strategies;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import configuration.PeerConfiguration;
import configuration.ProtocolVersion;
import files.FileManager;
import messages.Message;
import messages.MessageFactory;
import utils.Logger;

public class EnhancedRestoreStrategy extends RestoreStrategy {
    public EnhancedRestoreStrategy(PeerConfiguration configuration) {
        super(configuration);
    }

    public void sendChunk(Message msg) throws Exception {
        if (msg.getVersion().equals("1.0")) {
            new VanillaRestoreStrategy(configuration).sendChunk(msg);
            return;
        }

        ServerSocket socket = new ServerSocket(0);

        byte[] portData = String.valueOf(socket.getLocalPort()).getBytes();

        // send the chunk msg with the port
        byte[] chunkMsg = new MessageFactory(new ProtocolVersion(1, 1)).getChunkMessage(this.configuration.getPeerId(), msg.getFileId(), msg.getChunkNo(), portData);
        
        this.configuration.getMDR().send(chunkMsg);

        socket.setSoTimeout(5000);  
        try {
            Socket clientSocket = socket.accept();  // waits for connection for 5 seconds

            byte[] chunkData = new FileManager(configuration.getRootDir()).readChunk(msg.getFileId(), msg.getChunkNo());

            clientSocket.getOutputStream().write(chunkData);

            clientSocket.close();
            socket.close();
        }
        catch(SocketTimeoutException e) 
        {
            Logger.error("TCP socket timed out.");
        }
    }
}