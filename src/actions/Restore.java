package actions;

import java.util.concurrent.CompletableFuture;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileManager;
import messages.Message;
import messages.MessageFactory;
import server.FileBucket;
import sslengine.SSLClient;
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

            Message message = MessageFactory.getGetfileMessage(configuration.getPeerId(), fileKey, configuration.getChord().getSelf());
            Logger.debug(Logger.DebugType.RESTORE, "Sending " + message);

            configuration.getDataBucket().add(file.getFileKey(), new FileBucket((int) Math.ceil(file.getByteAmount() / 15000.), (byte[] data) -> {
                try {
                    new FileManager(configuration.getRootDir()).write(file.getFileName(), data);
                    if (data == null) throw new Exception("Couldn't receive file data!");
                    future.complete(new Result(true, "File '" + file.getFileName() + "' restored successfully!"));
                } catch (Exception e) {
                    Logger.error("writing restored file to filesystem", e, true);
                    future.complete(new Result(false, "Couldn't receive file data :("));
                }
            }, 500));

            int count = 0;
            while (true) {
                Message reply = SSLClient.sendQueued(configuration, destinationNode.getInetSocketAddress(), message, true).get();
                if (reply.getMessageType() == Message.MessageType.REDIRECT) destinationNode = reply.getNode();
                else if (reply.getMessageType() == Message.MessageType.PROCESSEDYES) break;
                else if (reply.getMessageType() == Message.MessageType.PROCESSEDNO) {
                    future.complete(new Result(false, "Couldn't find peer who has backed up '" + file.getFileName() + "' :("));
                    return;
                }
                else Logger.error("Wrong message type when handling reply to GETFILE message!");

                count++;
                if (count >= 500) {
                    Logger.error("Timeout in restore searching for the owner");
                    future.complete(new Result(false, "Timeout in search for the owner"));
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
