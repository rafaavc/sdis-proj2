package channels.handlers.strategies;

import java.util.concurrent.ScheduledThreadPoolExecutor;
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

public class EnhancedBackupStrategy extends BackupStrategy {
    private final ScheduledThreadPoolExecutor threadScheduler;

    public EnhancedBackupStrategy(PeerConfiguration configuration) throws ArgsException {
        super(configuration, new MessageFactory(new ProtocolVersion(1, 0))); // the messages are exactly equal to 1.0
        this.threadScheduler = configuration.getThreadScheduler();
    }

    public void backup(StoredTracker storedTracker, Message msg) throws Exception {

        threadScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                try
                {
                    if (storedTracker.getStoredCount(msg.getFileId(), msg.getChunkNo()) >= msg.getReplicationDeg()) return;

                    // check if still has space because in the time interval that passed the peer may have received other backups
                    if (configuration.getPeerState().getMaximumStorage() != -1 && configuration.getPeerState().getStorageAvailable() < msg.getBodySizeKB()) {
                        Logger.log("Not enough space available for backup.");
                        return;
                    }

                    Logger.log("Storing chunk.");
                    configuration.getMC().send(messageFactory.getStoredMessage(configuration.getPeerId(), msg.getFileId(), msg.getChunkNo()));

                    StoredTracker.addStoredCount(configuration.getPeerState(), msg.getFileId(), msg.getChunkNo(), configuration.getPeerId());

                    ChunkInfo chunk = new ChunkInfo(msg.getFileId(), msg.getBodySizeKB(), msg.getChunkNo(), storedTracker.getStoredCount(msg.getFileId(), msg.getChunkNo()), msg.getReplicationDeg());
                    configuration.getPeerState().addChunk(chunk);
                    
                    
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


                    FileManager files = new FileManager(configuration.getRootDir());

                    files.writeChunk(msg.getFileId(), msg.getChunkNo(), msg.getBody());

                
                    threadScheduler.schedule(new Runnable() {
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
                catch(Exception e) 
                {
                    Logger.error(e, true);
                }
            }
        }, configuration.getRandomDelay(2000, 400), TimeUnit.MILLISECONDS); // this has will be executed after 500 + rand(5000) ms, so that during the first 400 ms it received the STORED of the peers who already have the chunk backed up (which called the method below)
    }
    
    public void sendAlreadyHadStored(Message msg) {
        threadScheduler.schedule(new Runnable() {
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
