package actions;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import utils.Logger;
import utils.Result;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class FileSender {
    public static Result sendFile(PeerConfiguration configuration, FileRepresentation file, ChordNode destinationNode, Message startMessage) throws ExecutionException, InterruptedException {

        Future<Message> reply = SSLClient.sendQueued(configuration, destinationNode.getInetSocketAddress(), startMessage, true);
        reply.get();


        Logger.log("Sending the file (key=" + file.getFileKey() + ") up to " + destinationNode);
        Logger.debug(Logger.DebugType.FILETRANSFER, "Sending parts...");

        int totalAmount = 0, order = 1;
        while (totalAmount != file.getData().length)
        {
            int left = file.getData().length - totalAmount;

            int amount = Math.min(left, 15000);
            byte[] part = new byte[amount];

            System.arraycopy(file.getData(), totalAmount, part, 0, amount);

            Message dataMessage = MessageFactory.getDataMessage(configuration.getPeerId(), file.getFileKey(), order, part);
            SSLClient.sendQueued(configuration, destinationNode.getInetSocketAddress(), dataMessage, false);

            totalAmount += amount;
            order++;
        }

        Logger.debug(Logger.DebugType.FILETRANSFER, "Sent all parts!");
        return new Result(true, "Backed up successfully!");
    }
}
