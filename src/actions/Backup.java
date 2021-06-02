package actions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import state.MyFileInfo;
import utils.Logger;
import utils.Result;

public class Backup implements Action {
    private final PeerConfiguration configuration;
    private final String filePath;
    private final int desiredReplicationDegree, alreadyObtainedReplicationDeg;
    private final CompletableFuture<Result> future;
    private final boolean saveToState;
    private final FileRepresentation file;

    public Backup(CompletableFuture<Result> future, PeerConfiguration configuration, String filePath, int desiredReplicationDegree) throws Exception {
        this.configuration = configuration;
        this.filePath = filePath;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
        file = new FileRepresentation(filePath);
        alreadyObtainedReplicationDeg = 0;
        saveToState = true;
    }

    public Backup(CompletableFuture<Result> future, PeerConfiguration configuration, FileRepresentation file, int desiredReplicationDegree) {
        this.configuration = configuration;
        this.filePath = null;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
        this.file = file;
        saveToState = false;
        alreadyObtainedReplicationDeg = 0;
    }

    public void execute() {
        Logger.log("Executing backup of file " + (filePath == null ? file.getFileKey() : filePath));
        try
        {
            ChordNode destinationNode = configuration.getChord().lookup(file.getFileKey()).get();

            Message message = MessageFactory.getPutfileMessage(configuration.getPeerId(), file.getFileKey(),
                    (int) Math.ceil(file.getData().length / 15000.), desiredReplicationDegree, alreadyObtainedReplicationDeg, file.getData().length);
            Logger.debug(Logger.DebugType.BACKUP, "Sending " + message);

            
            int perceivedReplicationDegree = 0;
            int firstNodeId = destinationNode.getId();
            boolean first = true;
            while (perceivedReplicationDegree < desiredReplicationDegree)
            {
                if (!first && destinationNode.getId() == firstNodeId) break;
                else if (first) first = false;

                if (destinationNode.getId() == configuration.getChord().getId())
                {
                    destinationNode = configuration.getChord().getSuccessor();
                    continue;
                }

                Message reply = SSLClient.sendQueued(configuration, destinationNode.getInetSocketAddress(), message, true).get();
                switch (reply.getMessageType()) {
                    case PROCESSEDNO:
                        perceivedReplicationDegree++;
                        break;
                    case REDIRECT:
                        destinationNode = reply.getNode();
                        continue;
                    case PROCESSEDYES:
                        ChordNode node = destinationNode;
                        perceivedReplicationDegree++;
                        configuration.getThreadScheduler().execute(() -> {
                            try {
                                FileSender.sendFile(configuration, file, node);
                            } catch (Exception e) {
                                Logger.error("sending file to " + node, e, true);
                            }
                        });
                        break;
                    default:
                        Logger.error("Invalid reply during backing up...");
                }

                Message successor = SSLClient.sendQueued(configuration, destinationNode.getInetSocketAddress(),
                        MessageFactory.getGetSuccessorMessage(configuration.getPeerId()), true).get();
                destinationNode = successor.getNode();
            }

            if (perceivedReplicationDegree > 0)
            {
                if (saveToState) configuration.getPeerState().addFile(new MyFileInfo(filePath, file.getData().length, file.getFileKey(), desiredReplicationDegree, perceivedReplicationDegree));
                future.complete(new Result(true,
                        "File sent successfully! Perceived replication degree = " + perceivedReplicationDegree +
                                (perceivedReplicationDegree < desiredReplicationDegree ? " (<" + desiredReplicationDegree + ")" : "")));
                return;
            }
            future.complete(new Result(false, "Couldn't backup file! (perceived replication degree = 0)"));
        }
        catch(Exception e)
        {
            Logger.error("executing backup action", e, true);
            future.complete(new Result(false, e.getMessage()));
        }
    }
}
