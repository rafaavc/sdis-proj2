package actions;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import configuration.PeerConfiguration;
import files.Chunk;
import messages.trackers.StoredTracker;
import sslengine.SSLClient;
import state.ChunkPair;
import state.FileInfo;
import utils.Logger;
import utils.Result;

public class ChunksBackup extends Action implements Runnable {
    private final Map<Chunk, byte[]> chunksToSend;
    private int count, sleepAmount;
    private final PeerConfiguration configuration;
    private final FileInfo info;
    private final StoredTracker storedTracker;
    private final CompletableFuture<Result> future;

    public ChunksBackup(InetSocketAddress destination, SSLClient client, CompletableFuture<Result> future, StoredTracker storedTracker, PeerConfiguration configuration, FileInfo info, Map<Chunk, byte[]> chunksToSend) throws Exception {
        super(destination, client);
        this.count = 1;
        this.sleepAmount = configuration.getProtocolVersion().equals("1.0") ? 1000 : 3000;
        this.configuration = configuration;
        this.info = info;
        this.chunksToSend = chunksToSend;
        this.storedTracker = storedTracker;
        this.future = future;
    }

    private ChunksBackup(InetSocketAddress destination, SSLClient client, CompletableFuture<Result> future, StoredTracker storedTracker, PeerConfiguration configuration, FileInfo info, Map<Chunk, byte[]> chunksToSend, int count, int sleepAmount) throws Exception {
        this(destination, client, future, storedTracker, configuration, info, chunksToSend);
        this.count = count;
        this.sleepAmount = sleepAmount;
    }


    @Override
    public void run() {
        int desiredReplicationDegree = info.getDesiredReplicationDegree();

        try {
            for (Chunk chunk : chunksToSend.keySet()) 
            {
                byte[] msg = chunksToSend.get(chunk);
                // this.configuration.getMDB().send(msg);
                client.write(msg);
            } 
        }
        catch(Exception e) 
        {
            Logger.error(e, future);
            return;
        }

        configuration.getThreadScheduler().schedule(new Runnable() {
            @Override
            public void run() {
                Map<Chunk, byte[]> chunksToSendCopy = new HashMap<>(chunksToSend);

                for (Chunk chunk : chunksToSendCopy.keySet()) {
                    int replicationDegree = storedTracker.getStoredCount(chunk.getFileId(), chunk.getChunkNo());
                    
                    if (replicationDegree >= desiredReplicationDegree) {
                        info.addChunk(new ChunkPair(chunk.getChunkNo(), replicationDegree));
                        chunksToSend.remove(chunk);
                    }
                }


                if (count < 5 && chunksToSend.size() != 0)
                {
                    try {
                        configuration.getThreadScheduler().execute(new ChunksBackup(destination, client, future, storedTracker, configuration, info, chunksToSend, count+1, sleepAmount*2));
                    } catch (Exception e) {
                        Logger.error(e, true);
                    }
                    return;
                }

                StringBuilder builder = new StringBuilder();

                if (chunksToSend.size() != 0)
                {
                    for (Chunk chunk : chunksToSend.keySet()) {
                        int replicationDegree = storedTracker.getStoredCount(chunk.getFileId(), chunk.getChunkNo());
            
                        if (replicationDegree == 0) {
                            new Delete(new CompletableFuture<Result>(), configuration, info.getFileId()).execute();
                            StoredTracker.removeTracker(storedTracker);

                            String msg = "Wasn't able to backup file: chunk " + chunk.getChunkNo() + " was not backed up by any peers";
                            Logger.error(msg);
                            future.complete(new Result(false, msg));
                            shutdown();
                            return;
                        }
                        info.addChunk(new ChunkPair(chunk.getChunkNo(), replicationDegree));

                        String msg = "Couldn't backup chunk " + chunk.getChunkNo() + " with the desired replication degree. Perceived = " + replicationDegree;
                        Logger.log(msg);

                        builder.append(msg);
                        builder.append("\n");
                    }
                }
                StoredTracker.removeTracker(storedTracker);

                String msg = "Backed up successfully!";
                Logger.log(msg);
                builder.append(msg);
                future.complete(new Result(true, builder.toString()));
                shutdown();
            }
        }, sleepAmount, TimeUnit.MILLISECONDS);
    }
}
