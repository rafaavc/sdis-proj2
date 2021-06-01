package server.handlers;

import messages.Message;
import messages.trackers.PutchunkTracker;
import messages.trackers.StoredTracker;
import server.handlers.strategies.BackupStrategy;
import utils.Logger;

import java.net.InetAddress;

import configuration.PeerConfiguration;

public class BackupChannelHandler extends Handler {
    private final BackupStrategy backupStrategy;

    public BackupChannelHandler(PeerConfiguration configuration, BackupStrategy backupStrategy) {
        super(configuration);
        this.backupStrategy = backupStrategy;
    }

    public void execute(Message msg, InetAddress senderAddress) {
        switch(msg.getMessageType()) {
            case PUTFILE:
                try 
                {
                    StoredTracker storedTracker = StoredTracker.getNewTracker();

                    // peerState.removeDeletedFile(msg.getFileFe());
                    // PutchunkTracker.addPutchunkReceived(msg.getFileId(), msg.getChunkNo());
                    // if (peerState.hasChunk(msg.getFileId(), msg.getChunkNo())) {
                    //     Logger.log("Already had chunk!");
                    //     backupStrategy.sendAlreadyHadStored(msg);
                    //     break;
                    // } else if (peerState.ownsFileWithId(msg.getFileId())) {
                    //     Logger.log("I am the file owner!");
                    //     break;
                    // } else if (peerState.getMaximumStorage() != -1 && peerState.getStorageAvailable() < msg.getBodySizeKB()) {
                    //     Logger.log("Not enough space available for backup.");
                    //     break;
                    // }

                    backupStrategy.backup(storedTracker, msg);
                } 
                catch (Exception e) 
                {
                    Logger.error(e, true);
                }
                break;
            default:
                Logger.error("Received wrong message in BackupChannelHandler! " + msg);
                break;
        }
    }
}
