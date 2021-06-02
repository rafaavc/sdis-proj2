package actions;

import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import state.MyFileInfo;
import utils.Logger;
import utils.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Backup implements Action {
    private final PeerConfiguration configuration;
    private final String filePath;
    private final int desiredReplicationDegree;
    private final CompletableFuture<Result> future;
    private final boolean saveToState;
    private final FileRepresentation file;

    public Backup(CompletableFuture<Result> future, PeerConfiguration configuration, String filePath, int desiredReplicationDegree) throws Exception {
        this.configuration = configuration;
        this.filePath = filePath;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
        file = new FileRepresentation(filePath);
        saveToState = true;
    }

    public Backup(CompletableFuture<Result> future, PeerConfiguration configuration, FileRepresentation file, int desiredReplicationDegree) {
        this.configuration = configuration;
        this.filePath = null;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.future = future;
        this.file = file;
        saveToState = false;
    }

    public void execute() {
        Logger.log("Executing backup of file " + (filePath == null ? file.getFileKey() : filePath));
        try
        {
            ChordNode destinationNode = configuration.getChord().lookup(file.getFileKey()).get();

            Message message = MessageFactory.getPutfileMessage(configuration.getPeerId(), file.getFileKey(),
                    (int) Math.ceil(file.getData().length / 15000.), desiredReplicationDegree, file.getData().length);
            Logger.debug(Logger.DebugType.BACKUP, "Sending " + message);



            // will hold all the nodes that redirected since the last node that saved
            // these nodes remove their (dangling!) pointers because there was no
            // other node in front of them that stored the file
            List<ChordNode> nodesThatRedirected = new ArrayList<>();


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
                    configuration.getPeerState().addPointerFile(message.getFileKey());
                    nodesThatRedirected.add(configuration.getChord().getSelf());
                    continue;
                }

                Message reply = SSLClient.sendQueued(destinationNode, message, true).get();
                switch (reply.getMessageType()) {
                    case REDIRECT:
                        nodesThatRedirected.add(destinationNode);
                        destinationNode = reply.getNode();
                        continue;
                    case PROCESSEDYES:
                        ChordNode node = destinationNode;
                        configuration.getThreadScheduler().execute(() -> {
                            try {
                                Result res = FileSender.sendFile(configuration, file, node);
                                if (!res.success()) throw new Exception(res.getMessage());
                            } catch (Exception e) {
                                Logger.error("sending file to " + node, e, false);
                            }
                        });
                    case PROCESSEDNO:
                        perceivedReplicationDegree++;
                        nodesThatRedirected = new ArrayList<>();
                        break;
                    default:
                        Logger.error("Invalid reply during backing up...");
                }

                Message successor = SSLClient.sendQueued(destinationNode,
                        MessageFactory.getGetSuccessorMessage(configuration.getPeerId()), true).get();
                destinationNode = successor.getNode();
            }

            // tell the nodes that redirected after the last one who saved to remove their dangling pointers
            for (ChordNode node : nodesThatRedirected)
                SSLClient.sendQueued(node,
                        MessageFactory.getRemovePointerMessage(configuration.getPeerId(), message.getFileKey()), false);

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
