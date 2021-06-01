package server.handlers.strategies;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import configuration.PeerConfiguration;
import messages.Message;
import utils.Logger;

public class EnhancedRestoreStrategy extends RestoreStrategy {
    public EnhancedRestoreStrategy(PeerConfiguration configuration) {
        super(configuration);
    }

    public void sendChunk(Message msg) throws Exception {

        ServerSocket socket = new ServerSocket(0);

        byte[] portData = String.valueOf(socket.getLocalPort()).getBytes();

        // send the chunk msg with the port
        // byte[] chunkMsg = new MessageFactory(new ProtocolVersion(1, 1)).getChunkMessage(this.configuration.getPeerId(), msg.getFileId(), msg.getChunkNo(), portData);
        
        //this.configuration.getMDR().send(chunkMsg);
        //Logger.todo(this);

        socket.setSoTimeout(5000);  
        try {
            Socket clientSocket = socket.accept();  // waits for connection for 5 seconds

            // byte[] chunkData = new FileManager(configuration.getRootDir()).readChunk(msg.getFileId(), msg.getChunkNo());

            // clientSocket.getOutputStream().write(chunkData);

            clientSocket.close();
            socket.close();
        }
        catch(SocketTimeoutException e) 
        {
            Logger.error("TCP socket timed out.");
        }
    }
}