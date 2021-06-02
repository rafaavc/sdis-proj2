package server;

import configuration.PeerConfiguration;
import files.FileManager;
import messages.Message;
import messages.MessageFactory;
import state.OthersFileInfo;
import utils.Logger;

public class BackupHandler {
    private final DataBucket dataBucket;
    private final PeerConfiguration configuration;

    public BackupHandler(PeerConfiguration configuration, DataBucket dataBucket) {
        this.configuration = configuration;
        this.dataBucket = dataBucket;
    }

    public Message handle(Message message) {
        if (message.getMessageType() == Message.MessageType.PUTFILE) {
            try {
                Logger.debug(Logger.DebugType.BACKUP, "Received PUTFILE: " + message);

                // configuration.getPeerState().removeDeletedFile(message.getFileKey());   // remove the file from the deleted files list

                if (configuration.getPeerState().hasBackedUpFile(message.getFileKey()))
                {
                    Logger.log("I've already backed up the file.");
                    return MessageFactory.getProcessedNoMessage(configuration.getPeerId());
                }

                if ((configuration.getPeerState().getMaximumStorage() != -1 && configuration.getPeerState().getStorageAvailable() < message.getByteAmount() / 1000.)  // if no space available
                        || configuration.getPeerState().ownsFileWithKey(message.getFileKey()))   // or if owns the file
                {
                    if (configuration.getPeerState().ownsFileWithKey(message.getFileKey()))
                        Logger.log("I am the owner of the file.");
                    else
                        Logger.log("Not enough space available for backup.");

                    Logger.debug(Logger.DebugType.FILEPOINTER, "Added pointer (waspointer=" + configuration.getPeerState().isPointerFile(message.getFileKey()) + ") for file " + message.getFileKey());
                    configuration.getPeerState().addPointerFile(message.getFileKey()); // Store in state that the file is responsibility of the successor

                    return MessageFactory.getRedirectMessage(configuration.getPeerId(), configuration.getChord().getSuccessor());
                }


                Logger.log("Going to backup file " + message.getFileKey());
                dataBucket.add(message.getFileKey(), new FileBucket(message.getOrder(), (byte[] data) -> {
                    try {
                        if (data == null) {
                            Logger.error("Wasn't able to backup file " + message.getFileKey() + " :(");
                            return;
                        }

                        new FileManager(configuration.getRootDir()).writeBackedupFile(message.getFileKey(), data); // saving the file

                        configuration.getPeerState().addBackedUpFile(new OthersFileInfo(message.getFileKey(), data.length / (float) 1000., message.getReplicationDeg()));
                        Logger.log("Backed up file " + message.getFileKey() + "!");

                    } catch (Exception e) {
                        Logger.error("saving file data", e, true);
                    }
                }, 300));

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
