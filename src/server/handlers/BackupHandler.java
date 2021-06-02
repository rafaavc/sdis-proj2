package server.handlers;

import actions.Backup;
import files.FileManager;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import messages.trackers.PutchunkTracker;
import messages.trackers.StoredTracker;
import server.DataBucket;
import server.FileBucket;
import server.Router;
import server.handlers.strategies.BackupStrategy;
import state.OthersFileInfo;
import utils.Logger;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;

import configuration.PeerConfiguration;
import utils.Result;
import utils.ResultWithData;

public class BackupHandler extends Handler {
    private final DataBucket dataBucket;

    public BackupHandler(PeerConfiguration configuration, DataBucket dataBucket) {
        super(configuration);
        this.dataBucket = dataBucket;
    }

    public int sendToSuccessor(Message message, byte[] data, boolean stored, int alreadyPerceivedReplicationDegree) throws Exception {
        if (message.getReplicationDeg() > alreadyPerceivedReplicationDegree)
        {
            CompletableFuture<ResultWithData<Integer>> future = new CompletableFuture<>();

            Backup backupAction = new Backup(future, configuration,
                    new FileRepresentation(message.getFileKey(), data), message.getReplicationDeg(), alreadyPerceivedReplicationDegree);

            backupAction.execute();

            if (future.get().success()) alreadyPerceivedReplicationDegree += future.get().getData();
        }
        else Logger.debug(Logger.DebugType.BACKUP, "The file already has the desired replication degree!");

        return alreadyPerceivedReplicationDegree;
    }

    public int sendToSuccessor(Message message, boolean stored, int alreadyPerceivedReplicationDegree) throws Exception {
        byte[] data = new FileManager(configuration.getRootDir()).readBackedUpFile(message.getFileKey());
        return sendToSuccessor(message, data, stored, alreadyPerceivedReplicationDegree);
    }

    public Message handle(Message message) {
        if (message.getMessageType() == Message.MessageType.PUTFILE) {
            try {
                Logger.debug(Logger.DebugType.BACKUP, "Received PUTFILE: " + message);

                configuration.getPeerState().removeDeletedFile(message.getFileKey());   // remove the file from the deleted files list

                if (configuration.getPeerState().hasBackedUpFile(message.getFileKey()))
                {

                    int perceivedReplicationDegree = sendToSuccessor(message, true, message.getAlreadyPerceivedDegree() + 1);
                    // TODO send to the peer who sent the message

                    return MessageFactory.getProcessedNoMessage(configuration.getPeerId());
                }
                if (configuration.getPeerState().getMaximumStorage() != -1 && configuration.getPeerState().getStorageAvailable() < message.getByteAmount() / 1000.)
                {
                    Logger.log("Not enough space available for backup. Redirecting to successor.");

                    configuration.getPeerState().addPointerFile(message.getFileKey()); // Store in state that the file is responsibility of the successor

                    dataBucket.add(message.getFileKey(), new FileBucket(message.getOrder(), (byte[] data) -> {
                        try {
                            if (data == null) {
                                Logger.error("Wasn't able to get data of file " + message.getFileKey() + " to send to my successor :(");
                                return;
                            }
                            Logger.log("Sending the file " + message.getFileKey() + " to my successor!");

                            int perceivedReplicationDegree = sendToSuccessor(message, data, false, message.getAlreadyPerceivedDegree());
                            // TODO send to the peer who sent the message

                        } catch (Exception e) {
                            Logger.error("sending file to my successor after not being able to back it up", e, true);
                        }
                    }));

                    return MessageFactory.getProcessedYesMessage(configuration.getPeerId());
                }
                /*else if (peerState.ownsFileWithId(msg.getFileId())) {
                    Logger.log("I am the file owner!");
                    return null;
                }*/


                Logger.log("Going to backup file " + message.getFileKey());
                CompletableFuture<Result> f = new CompletableFuture<>();
                dataBucket.add(message.getFileKey(), new FileBucket(message.getOrder(), (byte[] data) -> {
                    try {
                        if (data == null) {
                            Logger.error("Wasn't able to backup file " + message.getFileKey() + " :(");
                            return;
                        }

                        new FileManager(configuration.getRootDir()).writeBackedupFile(message.getFileKey(), data); // saving the file

                        configuration.getPeerState().addBackedUpFile(new OthersFileInfo(message.getFileKey(), data.length / (float) 1000., message.getReplicationDeg()));
                        Logger.log("Backed up file " + message.getFileKey() + "!");

                        int perceivedReplicationDegree = sendToSuccessor(message, data, true, message.getAlreadyPerceivedDegree() + 1);
                        // TODO send to the peer who sent the message

                    } catch (Exception e) {
                        Logger.error("sending file to my successor after being able to back it up", e, true);
                    }
                }));

                return MessageFactory.getProcessedYesMessage(configuration.getPeerId());
            } catch (Exception e) {
                Logger.error(e, true);
                return null;
            }
        }
        Logger.error("Received wrong message in BackupHandler! " + message);
        return null;
    }
}
