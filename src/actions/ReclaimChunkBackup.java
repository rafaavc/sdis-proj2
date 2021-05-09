package actions;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import configuration.PeerConfiguration;
import messages.trackers.StoredTracker;
import state.ChunkInfo;
import utils.Logger;

public class ReclaimChunkBackup implements Runnable {
    private final ChunkInfo chunk;
    private int count, sleepAmount;
    private final PeerConfiguration configuration;
    private final byte[] putchunkMsg, storedMsg;
    private final StoredTracker storedTracker;

    public ReclaimChunkBackup(StoredTracker storedTracker, PeerConfiguration configuration, ChunkInfo chunk, byte[] putchunkMsg, byte[] storedMsg) {
        this.count = 1;
        this.sleepAmount = 1000;
        this.configuration = configuration;
        this.chunk = chunk;
        this.putchunkMsg = putchunkMsg;
        this.storedMsg = storedMsg;
        this.storedTracker = storedTracker;
    }

    private ReclaimChunkBackup(StoredTracker storedTracker, PeerConfiguration configuration, ChunkInfo chunk, byte[] putchunkMsg, byte[] storedMsg, int count, int sleepAmount) {
        this(storedTracker, configuration, chunk, putchunkMsg, storedMsg);
        this.count = count;
        this.sleepAmount = sleepAmount;
    }

    @Override
    public void run() {
        ScheduledThreadPoolExecutor threadScheduler = configuration.getThreadScheduler();

        try
        {
            configuration.getMDB().send(putchunkMsg);
        }
        catch(Exception e)
        {
            Logger.error(e, true);
        }

        int randVal = configuration.getRandomDelay(400);
        
        // sends the stored corresponding to this peer after the rand(400) ms delay
        threadScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                try
                {
                    configuration.getMC().send(storedMsg);
                }
                catch(Exception e)
                {
                    Logger.error(e, true);
                }

                // checks the replication degree after sleepAmount ms
                threadScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {                
                        int replicationDegree = storedTracker.getStoredCount(chunk.getFileId(), chunk.getChunkNo());

                        if (count < 5 && replicationDegree < chunk.getDesiredReplicationDegree())
                        {
                            threadScheduler.execute(new ReclaimChunkBackup(storedTracker, configuration, chunk, putchunkMsg, storedMsg, count + 1, sleepAmount * 2));
                            return;
                        }

                        StoredTracker.removeTracker(storedTracker);

                        if (replicationDegree == 0)
                            Logger.error("Couldn't backup chunk " + chunk.getChunkNo() + ". Perceived = " + replicationDegree);
                        
                        else if (replicationDegree < chunk.getDesiredReplicationDegree())
                            Logger.log("Couldn't backup chunk " + chunk.getChunkNo() + " with the desired replication degree. Perceived = " + replicationDegree);

                        else
                            Logger.log("Backed up chunk successfully.");

                        chunk.setPerceivedReplicationDegree(replicationDegree);
                    }
                }, sleepAmount - randVal, TimeUnit.MILLISECONDS);
            }
        }, randVal, TimeUnit.MILLISECONDS);
    }
}
