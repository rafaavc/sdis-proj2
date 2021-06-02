package actions;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import utils.Logger;
import utils.Result;

public class FileSender {
    private static final String successMessage = "File sent successfully! Replication degree = ";

    public static Result sendFile(PeerConfiguration configuration, FileRepresentation file, ChordNode destinationNode) throws Exception {
        Logger.log("Sending the file (key=" + file.getFileKey() + ") up to " + destinationNode);
        Logger.debug(Logger.DebugType.FILETRANSFER, "Sending parts...");

        int totalAmount = 0, order = 0;
        while (totalAmount != file.getData().length)
        {
            int left = file.getData().length - totalAmount;

            int amount = Math.min(left, 15000);
            byte[] part = new byte[amount];

            System.arraycopy(file.getData(), totalAmount, part, 0, amount);

            totalAmount += amount;
            order++;

            int orderSaved = order;
            configuration.getThreadScheduler().execute(() -> {
                Message dataMessage = MessageFactory.getDataMessage(configuration.getPeerId(), file.getFileKey(), orderSaved, part);
                try
                {
                    SSLClient.sendQueued(destinationNode, dataMessage, false);
                }
                catch (Exception e)
                {
                    Logger.error("sending file and waiting for data message reply", e, false);
                }
            });
        }

        Logger.debug(Logger.DebugType.FILETRANSFER, "Sent all parts!");

        return new Result(true, successMessage);
    }
}
