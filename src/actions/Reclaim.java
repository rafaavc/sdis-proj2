//package actions;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//
//import configuration.PeerConfiguration;
//import files.FileManager;
//import sslengine.SSLClient;
//import state.OthersFileInfo;
//import utils.Logger;
//import utils.Result;
//
//public class Reclaim {
//    private final PeerConfiguration configuration;
//    private final int availableSpaceDesired;
//    private final CompletableFuture<Result> future;
//
//    public Reclaim(CompletableFuture<Result> future, PeerConfiguration configuration, int availableSpaceDesired) {
//        this.configuration = configuration;
//        this.availableSpaceDesired = availableSpaceDesired;
//        this.future = future;
//    }
//
//    public void execute() {
//        try {
//            // space desired < 0 means no space limit
//            this.configuration.getPeerState().setMaximumStorageAvailable(availableSpaceDesired < 0 ? -1 : availableSpaceDesired);
//
//            // calcular espaço ocupado
//            float occupiedSpace = configuration.getPeerState().getOccupiedStorage();
//
//            Logger.log("Occupied space: " + occupiedSpace);
//
//            if (availableSpaceDesired < 0 || availableSpaceDesired >= occupiedSpace) {
//                future.complete(new Result(true, "Changed available space as requested."));
//                return;
//            }
//
//            List<OthersFileInfo> peerChunks = configuration.getPeerState().getOthersFiles();
//
//            // remover e mandar msg para cada chunk que seja necessário remover (os que tiverem perceived degree maior que o desired primeiro)
//            Collections.sort(peerChunks, new Comparator<OthersFileInfo>() {
//                @Override
//                public int compare(OthersFileInfo chunkInfo1, OthersFileInfo chunkInfo2) {
//                    int chunkInfo1Diff = chunkInfo1.getPerceivedReplicationDegree() - chunkInfo1.getDesiredReplicationDegree();
//                    int chunkInfo2Diff = chunkInfo2.getPerceivedReplicationDegree() - chunkInfo2.getDesiredReplicationDegree();
//                    return chunkInfo2Diff - chunkInfo1Diff; // order in descending
//                }
//            });
//
//            // Logger.log("Chunks ordered: " + peerChunks);
//
//            List<OthersFileInfo> chunksToRemove = new ArrayList<>();
//
//            while(occupiedSpace > availableSpaceDesired && peerChunks.size() != 0) {
//                OthersFileInfo chunk = peerChunks.get(0);
//                // if replication degree == 1 fails (shortcoming of the protocol)
//                chunksToRemove.add(chunk);
//                occupiedSpace -= chunk.getSize();
//                peerChunks.remove(0);
//            }
//
//            // Logger.log("Chunks to remove: " + chunksToRemove);
//
//            FileManager fileManager = new FileManager(this.configuration.getRootDir());
//            SSLClient client = new SSLClient(configuration.getServer().getAddress(), configuration.getServer().getPort());
//            client.connect();
//            for (OthersFileInfo chunk : chunksToRemove) {
//                // byte[] msg = new MessageFactory(new ProtocolVersion(1, 0)).getRemovedMessage(this.configuration.getPeerId(), chunk.getFileId(), chunk.getChunkNo());
//
//                //this.configuration.getMC().send(msg);
//                //Logger.todo(this);
//                // client.write(msg);
//                client.read();
//
//                fileManager.deleteChunk(chunk.getFileKey(), chunk.getChunkNo());
//                this.configuration.getPeerState().deleteChunk(chunk);
//            }
//
//            client.shutdown();
//
//            String msg = "Reclaimed space successfuly.";
//            Logger.log(msg);
//            future.complete(new Result(true, msg));
//
//        } catch(Exception e) {
//            Logger.error(e, future);
//        }
//    }
//}
