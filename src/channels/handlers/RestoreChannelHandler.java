package channels.handlers;

import messages.trackers.ChunkTracker;
import utils.Logger;
import messages.Message;

import java.net.InetAddress;
import java.net.Socket;

import configuration.PeerConfiguration;

public class RestoreChannelHandler extends Handler {
    public RestoreChannelHandler(PeerConfiguration configuration) {
        super(configuration);
    }

    public void execute(Message msg, InetAddress senderAddress) {
        ChunkTracker chunkTracker = configuration.getChunkTracker();
        
        try {
            switch(msg.getMessageType()) {
                case CHUNK:
                    if (msg.getVersion().equals("1.0"))
                    {
                        chunkTracker.addChunkReceived(msg.getFileId(), msg.getChunkNo(), msg.getBody());
                        return;
                    }
                    if (configuration.getProtocolVersion().equals("1.0") || !msg.getVersion().equals("1.1"))
                    {
                        // if the peer's protocol version is 1.0, then no other is accepted; otherwise, the version must be 1.1
                        Logger.error("Received unknown protocol version CHUNK message (" + msg.getVersion() + ").");
                        return;
                    }
                    if (!chunkTracker.isWaitingForChunk(msg.getFileId(), msg.getChunkNo()))
                    {
                        chunkTracker.addChunkReceived(msg.getFileId(), msg.getChunkNo());
                        return;
                    }

                    // only arrives here if the peer is in version 1.1 and the message is 1.1 (and is waiting for chunk)

                    Logger.log("Connecting to TCP: " + senderAddress.getHostAddress() + ":" + Integer.parseInt(new String(msg.getBody())));
                    Socket socket = new Socket(senderAddress, Integer.parseInt(new String(msg.getBody())));

                    byte[] chunkData = socket.getInputStream().readAllBytes();
                    
                    chunkTracker.addChunkReceived(msg.getFileId(), msg.getChunkNo(), chunkData);

                    socket.close();

                    break;
                default:
                    Logger.error("Received wrong message in RestoreChannelHandler! " + msg);
                    break;
            }
        } catch (Exception e) {
            Logger.error(e, true);
        }
    }
}
