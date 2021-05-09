package channels.handlers.strategies;

import java.util.concurrent.TimeUnit;

import configuration.PeerConfiguration;
import configuration.ProtocolVersion;
import exceptions.ArgsException;
import files.FileManager;
import messages.Message;
import messages.MessageFactory;
import messages.trackers.StoredTracker;
import state.ChunkInfo;
import utils.Logger;

public class VanillaBackupStrategy extends BackupStrategy {
    public VanillaBackupStrategy(PeerConfiguration configuration) throws ArgsException {
        super(configuration, new MessageFactory(new ProtocolVersion(1, 0)));
    }

    public void backup(StoredTracker storedTracker, Message msg) throws Exception {
        FileManager files = new FileManager(configuration.getRootDir());
        
        Logger.log("Storing chunk.");

        StoredTracker.addStoredCount(configuration.getPeerState(), msg.getFileId(), msg.getChunkNo(), this.configuration.getPeerId());
        ChunkInfo chunk = new ChunkInfo(msg.getFileId(), (float)(msg.getBody().length / 1000.), msg.getChunkNo(), storedTracker.getStoredCount(msg.getFileId(), msg.getChunkNo()), msg.getReplicationDeg());

        storedTracker.addNotifier(msg.getFileId(), msg.getChunkNo(), () -> {
            synchronized(chunk) {
                try {
                    int storedCount = storedTracker.getStoredCount(msg.getFileId(), msg.getChunkNo());
                    if (storedCount > chunk.getPerceivedReplicationDegree()) chunk.setPerceivedReplicationDegree(storedCount);
                } catch(Exception e){
                    Logger.error(e, true);
                }
            }
        });

        configuration.getPeerState().addChunk(chunk);

        files.writeChunk(msg.getFileId(), msg.getChunkNo(), msg.getBody());

        sendStored(msg);

        configuration.getThreadScheduler().schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    int countsReceived = storedTracker.getStoredCount(msg.getFileId(), msg.getChunkNo());
                    chunk.setPerceivedReplicationDegree(countsReceived);
                } catch(Exception e) {
                    Logger.error(e, true);
                }
                StoredTracker.removeTracker(storedTracker);
            }
        }, 10, TimeUnit.SECONDS);
    }

    public void sendAlreadyHadStored(Message msg) {
        sendStored(msg);
    }

    private void sendStored(Message msg) {
        configuration.getThreadScheduler().schedule(new Runnable() {
            @Override
            public void run() {
                try
                {
                    configuration.getMC().send(messageFactory.getStoredMessage(configuration.getPeerId(), msg.getFileId(), msg.getChunkNo()));
                } 
                catch(Exception e) 
                {
                    Logger.error(e, true);
                }
            }
        }, configuration.getRandomDelay(400), TimeUnit.MILLISECONDS);
    }
}
