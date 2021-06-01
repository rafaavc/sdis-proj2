package actions;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import state.MyFileInfo;
import utils.Logger;
import utils.Result;

public class Backup implements Action {
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

            FileRepresentation file = new FileRepresentation(filePath);
            MyFileInfo info = new MyFileInfo(filePath, file.getFileKey(), desiredReplicationDegree);

            Future<ChordNode> destinationNode = configuration.getChord().lookup(info.getFileKey());


            Message message = MessageFactory.getPutfileMessage(configuration.getPeerId(), file.getFileKey(), (int) Math.ceil(file.getData().length / 15000.), desiredReplicationDegree);
            Logger.debug(Logger.DebugType.BACKUP, "Sending " + message);
            configuration.getPeerState().addFile(info);



            future.complete(FileSender.sendFile(configuration, file, destinationNode.get(), message));

        } catch(Exception e) {
            Logger.error(e, future);
        }
    }
}
