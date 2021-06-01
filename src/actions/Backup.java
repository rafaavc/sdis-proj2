package actions;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import state.MyFileInfo;
import utils.Logger;
import utils.Result;

public class Backup implements Action {
    private final PeerConfiguration configuration;
    private final String filePath;
    private final int desiredReplicationDegree;
    private final CompletableFuture<Result> future;

    public Backup(CompletableFuture<Result> future, PeerConfiguration configuration, String filePath, int desiredReplicationDegree) throws Exception {
        this.configuration = configuration;
        this.filePath = filePath;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
    }

    public void execute() {
        try {

            FileRepresentation file = new FileRepresentation(filePath);
            MyFileInfo info = new MyFileInfo(filePath, file.getFileKey(), desiredReplicationDegree);

            Future<ChordNode> destinationNode = configuration.getChord().lookup(info.getFileKey());

            Message message = MessageFactory.getPutfileMessage(configuration.getPeerId(), file.getFileKey(), (int) Math.ceil(file.getData().length / 15000.), desiredReplicationDegree);
            Logger.debug(Logger.DebugType.BACKUP, "Sending " + message);
            configuration.getPeerState().addFile(info);

            Future<Message> reply = SSLClient.sendQueued(configuration, destinationNode.get().getInetSocketAddress(), message, true);
            Logger.debug(Logger.DebugType.BACKUP, "Got reply = " + reply.get());


            Logger.debug(Logger.DebugType.BACKUP, "Sending parts...");
            int totalAmount = 0, order = 1;
            while (totalAmount != file.getData().length)
            {
                int left = file.getData().length - totalAmount;

                int amount = Math.min(left, 15000);
                byte[] part = new byte[amount];

                System.arraycopy(file.getData(), totalAmount, part, 0, amount);

                Message dataMessage = MessageFactory.getDataMessage(configuration.getPeerId(), file.getFileKey(), order, part);
                SSLClient.sendQueued(configuration, destinationNode.get().getInetSocketAddress(), dataMessage, false);

                totalAmount += amount;
                order++;
            }

            Logger.debug(Logger.DebugType.BACKUP, "Sent all parts!");

        } catch(Exception e) {
            Logger.error(e, future);
        }
    }
}
