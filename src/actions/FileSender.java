package actions;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import utils.Logger;
import utils.ResultWithData;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class FileSender {
    private static final String successMessage = "File sent successfully! Replication degree = ";

    public static ResultWithData<Integer> sendFile(PeerConfiguration configuration, FileRepresentation file, ChordNode destinationNode, Message startMessage) throws Exception {

        Message reply = SSLClient.sendQueued(configuration, destinationNode.getInetSocketAddress(), startMessage, true).get();

        if (reply.getMessageType() != Message.MessageType.PROCESSEDNO && reply.getMessageType() != Message.MessageType.PROCESSEDYES)
            return new ResultWithData<>(false, "Received wrong response to PUTFILE message!", -1);

        if (reply.getMessageType() == Message.MessageType.PROCESSEDNO)
        {
            int perceivedReplicationDegree = -1; // TODO ask for the perceived replication degree
            return new ResultWithData<>(true, successMessage + perceivedReplicationDegree, perceivedReplicationDegree);
        }


        Logger.log("Sending the file (key=" + file.getFileKey() + ") up to " + destinationNode);
        Logger.debug(Logger.DebugType.FILETRANSFER, "Sending parts...");

        int totalAmount = 0, order = 0;
        List<Future<Boolean>> futures = new ArrayList<>();
        while (totalAmount != file.getData().length)
        {
            int left = file.getData().length - totalAmount;

            int amount = Math.min(left, 15000);
            byte[] part = new byte[amount];

            System.arraycopy(file.getData(), totalAmount, part, 0, amount);

            totalAmount += amount;
            order++;

            CompletableFuture<Boolean> f = new CompletableFuture<>();
            futures.add(f);

            int orderSaved = order;
            configuration.getThreadScheduler().execute(() -> {
                Message dataMessage = MessageFactory.getDataMessage(configuration.getPeerId(), file.getFileKey(), orderSaved, part);
                try
                {
                    Message dataReply = SSLClient.sendQueued(configuration, destinationNode.getInetSocketAddress(), dataMessage, true).get();
                    f.complete(dataReply != null);
                }
                catch (Exception e)
                {
                    Logger.error("sending file and waiting for data message reply");
                    f.complete(false);
                }
            });
        }

        for (Future<Boolean> f : futures) {
            if (!f.get()) {
                return new ResultWithData<>(false, "Error while sending data to peer.", 0);
            }
        }

        Logger.debug(Logger.DebugType.FILETRANSFER, "Sent all parts!");

        int perceivedReplicationDegree = -1; // TODO ask for perceived replication degree
        return new ResultWithData<>(true, successMessage, -1);//perceivedReplicationDegree);
    }
}
