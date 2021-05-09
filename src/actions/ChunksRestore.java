package actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import configuration.PeerConfiguration;
import files.FileManager;
import messages.trackers.ChunkTracker;
import state.ChunkPair;
import state.FileInfo;
import utils.Logger;
import utils.Result;

public class ChunksRestore implements Runnable {
    private final Map<ChunkPair, byte[]> chunksToGet;
    private int count, sleepAmount;
    private final PeerConfiguration configuration;
    private final FileInfo info;
    private final CompletableFuture<Result> future;

    public ChunksRestore(CompletableFuture<Result> future, PeerConfiguration configuration, FileInfo info, Map<ChunkPair, byte[]> chunksToGet) {
        this.count = 1;
        this.sleepAmount = 1000;
        this.configuration = configuration;
        this.info = info;
        this.chunksToGet = chunksToGet;
        this.future = future;
    }

    private ChunksRestore(CompletableFuture<Result> future, PeerConfiguration configuration, FileInfo info, Map<ChunkPair, byte[]> chunksToGet, int count, int sleepAmount) {
        this(future, configuration, info, chunksToGet);
        this.count = count;
        this.sleepAmount = sleepAmount;
    }


    @Override
    public void run() {
        ChunkTracker chunkTracker = configuration.getChunkTracker();

        try {
            for (ChunkPair chunk : chunksToGet.keySet()) 
            {
                if (chunkTracker.hasReceivedChunk(info.getFileId(), chunk.getChunkNo())) continue;
                byte[] msg = chunksToGet.get(chunk);        
                this.configuration.getMC().send(msg);
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
                Map<ChunkPair, byte[]> chunksToGetCopy = new HashMap<>(chunksToGet);

                for (ChunkPair chunk : chunksToGetCopy.keySet()) {
                    if (chunkTracker.hasReceivedChunkData(info.getFileId(), chunk.getChunkNo())) {
                        chunksToGet.remove(chunk);
                    }
                }


                if (count < 5 && chunksToGet.size() != 0)
                {
                    configuration.getThreadScheduler().execute(new ChunksRestore(future, configuration, info, chunksToGet, count+1, sleepAmount*2));
                    return;
                }

                if (chunksToGet.size() != 0)
                {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Couldn't restore file, chunks missing:\n");
                    for (ChunkPair chunk : chunksToGet.keySet()) {
                        builder.append("- " + chunk.getChunkNo() + "\n");
                    }
                    builder.append("\n");
                    Logger.log(builder.toString());
                    future.complete(new Result(false, builder.toString()));
                    return;
                }

                List<byte[]> chunks = chunkTracker.getFileChunks(info.getFileId());
                String msg = "Received " + chunks.size() + "/" + info.getChunks().size() + " chunks.";
                Logger.log(msg);
                future.complete(new Result(true, msg));
    
                FileManager fileManager = new FileManager(configuration.getRootDir());

                try
                {
                    fileManager.writeFile(info.getFileName(), chunks);
                } 
                catch( Exception e)
                {
                    Logger.error(e, future);
                }
            }
        }, sleepAmount, TimeUnit.MILLISECONDS);
    }
}
