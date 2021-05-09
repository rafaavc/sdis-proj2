package actions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import configuration.PeerConfiguration;
import configuration.ProtocolVersion;
import files.Chunk;
import files.ChunkedFile;
import messages.MessageFactory;
import messages.trackers.StoredTracker;
import state.FileInfo;
import utils.Logger;
import utils.Result;

public class Backup {
    private final PeerConfiguration configuration;
    private final String filePath;
    private final int desiredReplicationDegree;
    private final CompletableFuture<Result> future;

    public Backup(CompletableFuture<Result> future, PeerConfiguration configuration, String filePath, int desiredReplicationDegree) {
        this.configuration = configuration;
        this.filePath = filePath;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
    }

    public void execute() {
        try {

            ChunkedFile file = new ChunkedFile(filePath);
            FileInfo info = new FileInfo(filePath, file.getFileId(), desiredReplicationDegree);

            Map<Chunk, byte[]> chunksToSend = new HashMap<>();

            for (Chunk chunk : file.getChunks())
            {   
                byte[] msg = new MessageFactory(new ProtocolVersion(1, 0)).getPutchunkMessage(this.configuration.getPeerId(), file.getFileId(), desiredReplicationDegree, chunk.getChunkNo(), chunk.getData());
                chunksToSend.put(chunk, msg);
            }

            Logger.log("I split the file into these chunks: " + chunksToSend);

            this.configuration.getPeerState().addFile(info);

            StoredTracker storedTracker = StoredTracker.getNewTracker();

            configuration.getThreadScheduler().execute(new ChunksBackup(future, storedTracker, configuration, info, chunksToSend));
            
        } catch(Exception e) {
            Logger.error(e, future);
        }
    }
}
