package actions;

import java.util.concurrent.CompletableFuture;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import state.MyFileInfo;
import utils.Logger;
import utils.ResultWithData;

public class Backup implements Action {
    private final PeerConfiguration configuration;
    private final String filePath;
    private final int desiredReplicationDegree, alreadyObtainedReplicationDeg;
    private final CompletableFuture<ResultWithData<Integer>> future;
    private final boolean backingUp;
    private final boolean saveToState;
    private final FileRepresentation file;

    public Backup(CompletableFuture<ResultWithData<Integer>> future, PeerConfiguration configuration, String filePath, int desiredReplicationDegree) throws Exception {
        this.configuration = configuration;
        this.filePath = filePath;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
        backingUp = true;
        file = new FileRepresentation(filePath);
        alreadyObtainedReplicationDeg = 0;
        saveToState = true;
    }

    public Backup(CompletableFuture<ResultWithData<Integer>> future, PeerConfiguration configuration, FileRepresentation file, int desiredReplicationDegree, int alreadyObtainedReplicationDeg) {
        this.configuration = configuration;
        this.filePath = null;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
        this.file = file;
        this.alreadyObtainedReplicationDeg = alreadyObtainedReplicationDeg;
        backingUp = false;
        saveToState = false;
    }

    public Backup(CompletableFuture<ResultWithData<Integer>> future, PeerConfiguration configuration, FileRepresentation file, int desiredReplicationDegree) {
        this.configuration = configuration;
        this.filePath = null;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
        this.file = file;
        backingUp = true;
        saveToState = false;
        alreadyObtainedReplicationDeg = 0;
    }

    public void execute() {
        Logger.log(backingUp ? "Executing backup of file " + filePath : "Sending file " + file.getFileKey() + " to my successor");
        try
        {
            ChordNode destinationNode;
            if (backingUp) destinationNode = configuration.getChord().lookup(file.getFileKey()).get();
            else destinationNode = configuration.getChord().getSuccessor();

            Message message = MessageFactory.getPutfileMessage(configuration.getPeerId(), file.getFileKey(), (int) Math.ceil(file.getData().length / 15000.), desiredReplicationDegree, alreadyObtainedReplicationDeg);
            Logger.debug(Logger.DebugType.BACKUP, "Sending " + message);

            ResultWithData<Integer> result = FileSender.sendFile(configuration, file, destinationNode, message);
            if (result.success()) {
                int perceivedReplicationDegree = result.getData();
                if (backingUp && saveToState) configuration.getPeerState().addFile(new MyFileInfo(filePath, file.getFileKey(), desiredReplicationDegree, perceivedReplicationDegree));
            }
            future.complete(result);
        }
        catch(Exception e)
        {
            Logger.error("executing backup action", e, true);
            future.complete(new ResultWithData<>(false, e.getMessage(), -1));
        }
    }
}
