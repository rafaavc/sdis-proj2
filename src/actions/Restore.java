//package actions;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.CompletableFuture;
//
//import configuration.PeerConfiguration;
//import messages.trackers.ChunkTracker;
//import state.ChunkPair;
//import state.MyFileInfo;
//import utils.Logger;
//import utils.Result;
//
//public class Restore {
//    private final PeerConfiguration configuration;
//    private final int fileKey;
//    private final CompletableFuture<Result> future;
//
//    public Restore(CompletableFuture<Result> future, PeerConfiguration configuration, int fileKey) {
//        this.configuration = configuration;
//        this.fileKey = fileKey;
//        this.future = future;
//    }
//
//    public void execute() {
//        try {
//            MyFileInfo file = configuration.getPeerState().getFile(fileKey);
//            ChunkTracker chunkTracker = configuration.getChunkTracker();
//
//            Map<ChunkPair, byte[]> chunksToGet = new HashMap<>();
//
////            for (ChunkPair chunk : file.getChunks())
////            {
////                // byte[] msg = new MessageFactory(configuration.getProtocolVersion()).getGetchunkMessage(this.configuration.getPeerId(), file.getFileId(), chunk.getChunkNo());
////                // chunksToGet.put(chunk, msg);
////            }
////
////            for (ChunkPair chunk : chunksToGet.keySet()) {
////                chunkTracker.startWaitingForChunk(file.getFileId(), chunk.getChunkNo());
////            }
//
//            configuration.getThreadScheduler().execute(new ChunksRestore(future, configuration, file, chunksToGet));
//        }
//        catch(Exception e)
//        {
//            Logger.error(e, future);
//        }
//    }
//}
