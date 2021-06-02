package actions;

import java.util.concurrent.CompletableFuture;

import chord.ChordNode;
import configuration.PeerConfiguration;
import messages.Message;
import messages.Message.MessageType;
import messages.MessageFactory;
import sslengine.SSLClient;
import utils.Logger;
import utils.Result;

public class Delete {
    private final PeerConfiguration configuration;
    private final int fileKey;
    private final CompletableFuture<Result> future;

    public Delete(CompletableFuture<Result> future, PeerConfiguration configuration, int fileKey) {
        this.configuration = configuration;
        this.fileKey = fileKey;
        this.future = future;
    }

    public void execute() {
        
        try 
        {
            ChordNode destinationNode = configuration.getChord().lookup(fileKey).get();

            while(true) {
                if (destinationNode == null) {
                    future.complete(new Result(false, "Couldn't get result of lookup of key " + fileKey));
                    return;
                }

                Message reply = SSLClient.sendQueued(configuration, destinationNode, MessageFactory.getDeleteMessage(configuration.getPeerId(), fileKey), true).get();
                if (reply == null) {
                    future.complete(new Result(false, "Couldn't get reply to DELETE to " + destinationNode));
                    return;
                }
                
                if (reply.getMessageType() == MessageType.PROCESSEDYES) {
                    Message successor = SSLClient.sendQueued(configuration, destinationNode, MessageFactory.getGetSuccessorMessage(configuration.getPeerId()), true).get();
                    if (successor == null) {
                        future.complete(new Result(false, "Couldn't get reply to GETSUCCESSOR to " + destinationNode));
                        return;
                    }

                    destinationNode = successor.getNode();

                } else if (reply.getMessageType() == MessageType.PROCESSEDNO) {
                    this.configuration.getPeerState().deleteFile(fileKey);
                    future.complete(new Result(true, "File " + fileKey + " successfully deleted!"));
                    return;
                } else {
                    future.complete(new Result(false, "Received unknown message as response to DELETE"));
                    return;
                }
            }
        } 
        catch(Exception e) 
        {
            Logger.error(e, future);
        }
    }
}
