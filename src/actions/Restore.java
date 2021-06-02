package actions;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import chord.ChordNode;
import configuration.PeerConfiguration;·
import files.FileManager;
import messages.Message;
import messages.MessageFactory;
import server.FileBucket;
import sslengine.SSLClient;
import state.ChunkPair;
import state.MyFileInfo;
import utils.Logger;
import utils.Result;


public class Restore {
    private final PeerConfiguration configuration;
    private final CompletableFuture<Result> future;
    private final int fileKey;

    public Restore(CompletableFuture<Result> future, PeerConfiguration configuration, int fileKey) {
        this.configuration = configuration;
        this.fileKey = fileKey;
        this.future = future;
    }

    public void execute() {
        try {
            MyFileInfo file = configuration.getPeerState().getFile(fileKey);

            ChordNode destinationNode = configuration.getChord().lookup(fileKey).get();

            Message message = MessageFactory.getGetfileMessage(configuration.getPeerId(), fileKey);
            Logger.debug(Logger.DebugType.RESTORE, "Sending " + message);


            // TODO send timeout to file bucket as parameter
            configuration.getDataBucket().add(file.getFileKey(), new FileBucket((int) Math.ceil(file.getByteAmount() / 15000.), (byte[] data) -> {
                try {
                    new FileManager(configuration.getRootDir()).write(file.getFileName(), data);
                    if (data == null) throw new Exception("Couldn't receive file data!");
                } catch (Exception e) {
                    Logger.error("writing restored file to filesystem");
                    future.complete(new Result(false, "Couldn't receive file data :("));
                }
            }));

            // TODO timeout
            while (true) {
                Message reply = SSLClient.sendQueued(configuration, destinationNode.getInetSocketAddress(), message, true).get();
                if (reply.getMessageType() == Message.MessageType.REDIRECT) destinationNode = reply.getNode();
                else if (reply.getMessageType() == Message.MessageType.PROCESSEDYES) break;
                else Logger.error("Wrong message type when handling reply to GETFILE message!");
            }
        }
        catch(Exception e)
        {
            Logger.error(e, future);
        }
    }
}
