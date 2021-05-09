package channels.handlers;

import messages.trackers.ChunkTracker;
import messages.trackers.DeleteTracker;
import messages.Message;
import messages.MessageFactory;
import messages.trackers.StoredTracker;
import messages.trackers.PutchunkTracker;
import state.ChunkInfo;
import state.ChunkPair;
import state.FileInfo;
import utils.Logger;

import java.net.InetAddress;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import actions.ReclaimChunkBackup;
import channels.handlers.strategies.RestoreStrategy;
import configuration.PeerConfiguration;
import configuration.ProtocolVersion;
import files.FileManager;

public class ControlChannelHandler extends Handler {
    private final RestoreStrategy restoreStrategy;

    public ControlChannelHandler(PeerConfiguration configuration, RestoreStrategy restoreStrategy) {
        super(configuration);
        this.restoreStrategy = restoreStrategy;
    }

    public void execute(Message msg, InetAddress senderAddress) {
        FileManager fileManager = new FileManager(this.configuration.getRootDir());
        ChunkTracker chunkTracker = configuration.getChunkTracker();
        ScheduledThreadPoolExecutor threadScheduler = configuration.getThreadScheduler();

        try {
            MessageFactory msgFactoryVanilla = new MessageFactory(new ProtocolVersion(1, 0));
            switch(msg.getMessageType()) { 
                case STORED:
                    peerState.removeDeletedFile(msg.getFileId());  // the file was stored by other peer
                    StoredTracker.addStoredCount(peerState, msg.getFileId(), msg.getChunkNo(), msg.getSenderId());
                    break;
                case DELETE:
                    if (peerState.hasFileChunks(msg.getFileId())) {
                        peerState.deleteFileChunks(msg.getFileId());
                        fileManager.deleteFileChunks(msg.getFileId());
                    }
                    peerState.addDeletedFile(msg.getFileId());
                    DeleteTracker.addDeleteReceived(msg.getFileId());
                    break;
                case FILECHECK:
                    if (configuration.getProtocolVersion().equals("1.1") && peerState.isDeleted(msg.getFileId())) {
                        DeleteTracker deleteTracker = DeleteTracker.getNewTracker();

                        threadScheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                if (deleteTracker.hasReceivedDelete(msg.getFileId())) return;

                                DeleteTracker.removeTracker(deleteTracker);

                                try 
                                {
                                    byte[] deleteMsg = msgFactoryVanilla.getDeleteMessage(configuration.getPeerId(), msg.getFileId());
                                    configuration.getMC().send(deleteMsg);
                                } 
                                catch(Exception e) 
                                {
                                    Logger.error(e, true);
                                }
                            }
                        }, configuration.getRandomDelay(400), TimeUnit.MILLISECONDS);

                    }
                    break;
                case GETCHUNK:
                    if (peerState.hasChunk(msg.getFileId(), msg.getChunkNo())) {

                        threadScheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                try 
                                {
                                    if (chunkTracker.hasReceivedChunk(msg.getFileId(), msg.getChunkNo())) return;
                                    restoreStrategy.sendChunk(msg);
                                } 
                                catch(Exception e) 
                                {
                                    Logger.error(e, true);
                                }
                            }
                        }, configuration.getRandomDelay(400), TimeUnit.MILLISECONDS);
                    }
                    break;
                case REMOVED:
                    if (peerState.ownsFileWithId(msg.getFileId()))
                    {
                        FileInfo file = peerState.getFile(msg.getFileId());
                        ChunkPair chunk = file.getChunk(msg.getChunkNo());
                        StoredTracker removedStoredTracker = StoredTracker.getNewTracker();
                        
                        chunk.setPerceivedReplicationDegree(chunk.getPerceivedReplicationDegree() - 1);

                        removedStoredTracker.addNotifier(msg.getFileId(), msg.getChunkNo(), () -> {
                            synchronized(chunk) {
                                try {
                                    int storedCount = removedStoredTracker.getStoredCount(msg.getFileId(), msg.getChunkNo());
                                    if (storedCount > chunk.getPerceivedReplicationDegree()) chunk.setPerceivedReplicationDegree(storedCount);
                                } catch(Exception e){
                                    Logger.error(e, true);
                                }
                            }
                        });

                        threadScheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                StoredTracker.removeTracker(removedStoredTracker);
                            }
                        }, 10, TimeUnit.SECONDS);
                    }
                    else if (peerState.hasChunk(msg.getFileId(), msg.getChunkNo())) 
                    {
                        // So that previously received stored don't influence the outcome
                        StoredTracker removedStoredTracker = StoredTracker.getNewTracker();

                        // so that previously received putchunks don't matter
                        PutchunkTracker putchunkTracker = PutchunkTracker.getNewTracker();

                        ChunkInfo chunk = peerState.getChunk(msg.getFileId(), msg.getChunkNo());

                        chunk.setPerceivedReplicationDegree(chunk.getPerceivedReplicationDegree() - 1);

                        // if there is no need to backup the chunk
                        if (chunk.getPerceivedReplicationDegree() >= chunk.getDesiredReplicationDegree()) break;

                        byte[] chunkData = fileManager.readChunk(chunk.getFileId(), chunk.getChunkNo());

                        threadScheduler.schedule(new Runnable() {
                            @Override
                            public void run() {
                                try
                                {
                                    // if received putchunk abort (another peer already initiated backup)
                                    if (putchunkTracker.hasReceivedPutchunk(chunk.getFileId(), chunk.getChunkNo())) return;

                                    PutchunkTracker.removeTracker(putchunkTracker);

                                    Logger.log("Restarting backup of (" + chunk + ") after receiving REMOVED.");

                                    // because this peer already has the chunk
                                    StoredTracker.addStoredCount(peerState, msg.getFileId(), msg.getChunkNo(), configuration.getPeerId());

                                    byte[] putchunkMsg = msgFactoryVanilla.getPutchunkMessage(configuration.getPeerId(), chunk.getFileId(), chunk.getDesiredReplicationDegree(), chunk.getChunkNo(), chunkData);
                                    byte[] storedMsg = msgFactoryVanilla.getStoredMessage(configuration.getPeerId(), chunk.getFileId(), chunk.getChunkNo());

                                    threadScheduler.execute(new ReclaimChunkBackup(removedStoredTracker, configuration, chunk, putchunkMsg, storedMsg));
                                } 
                                catch(Exception e) 
                                {
                                    Logger.error(e, true);
                                }
                            }
                        }, configuration.getRandomDelay(400), TimeUnit.MILLISECONDS);
                    }
                    
                    break;
                default:
                    Logger.error("Received wrong message in ControlChannelHandler! " + msg);
                    break;
            }
        } catch (Exception e) {
            Logger.error(e, true);
        }
    }
}
