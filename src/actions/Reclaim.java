package actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import configuration.PeerConfiguration;
import files.FileManager;
import files.FileRepresentation;
import sslengine.SSLClient;
import state.OthersFileInfo;
import state.PeerState;
import utils.Logger;
import utils.Result;
import utils.ResultWithData;

public class Reclaim {
   private final PeerConfiguration configuration;
   private final int availableSpaceDesired;
   private final CompletableFuture<Result> future;

   public Reclaim(CompletableFuture<Result> future, PeerConfiguration configuration, int availableSpaceDesired) {
       this.configuration = configuration;
       this.availableSpaceDesired = availableSpaceDesired;
       this.future = future;
   }

   public void execute() {
       try {
            PeerState state = this.configuration.getPeerState();
            
            // space desired < 0 means no space limit
            state.setMaximumStorageAvailable(availableSpaceDesired < 0 ? -1 : availableSpaceDesired);

            // calculate occupied space
            float occupiedSpace = state.getOccupiedStorage();

            Logger.log("Occupied space: " + occupiedSpace);

            if (availableSpaceDesired < 0 || availableSpaceDesired >= occupiedSpace) {
                future.complete(new Result(true, "Changed available space as requested."));
                return;
            }

            List<OthersFileInfo> peerFiles = state.getOthersFiles();

            // remover e mandar msg para cada file que seja necessário remover (os que tiverem perceived degree maior que o desired primeiro)
            Collections.sort(peerFiles, new Comparator<OthersFileInfo>() {
                @Override
                public int compare(OthersFileInfo fileInfo1, OthersFileInfo fileInfo2) {
                    float file1 = fileInfo1.getSize();
                    float file2 = fileInfo2.getSize();
                    return (int) file1 - (int) file2; // order in ascending
                }
            });

            Logger.log("Files ordered: " + peerFiles);

            List<OthersFileInfo> filesToRemove = new ArrayList<>();

            while(occupiedSpace > availableSpaceDesired && peerFiles.size() != 0) {
                OthersFileInfo file = peerFiles.get(0);
                filesToRemove.add(file);
                occupiedSpace -= file.getSize();
                peerFiles.remove(0);
            }

            Logger.log("files to remove: " + filesToRemove);

            FileManager fileManager = new FileManager(this.configuration.getRootDir());

            for (OthersFileInfo file : filesToRemove) {
                CompletableFuture<ResultWithData<Integer>> auxFuture = new CompletableFuture<>();

                Backup backupAction = new Backup(auxFuture, configuration,
                        new FileRepresentation(file.getFileKey(), fileManager.readBackedUpFile(file.getFileKey())), 1);

                backupAction.execute();

                fileManager.deleteBackedUpFile(file.getFileKey());
                state.deleteOthersFile(file.getFileKey());
            }

            String msg = "Reclaimed space successfuly.";
            Logger.log(msg);
            future.complete(new Result(true, msg));

        } catch(Exception e) {
            Logger.error(e, future);
        }
    }
}
