package server;

import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;

import actions.FileSender;
import chord.Chord;
import chord.ChordNode;
import configuration.PeerConfiguration;
import files.FileManager;
import files.FileRepresentation;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import state.MyFileInfo;
import state.OthersFileInfo;
import utils.Logger;
import utils.Logger.DebugType;

public class ServerRouter implements Router {
    
    private final PeerConfiguration configuration;
    private final DataBucket dataBucket;
    private final BackupHandler backupHandler;

    public ServerRouter(PeerConfiguration configuration) {
        this.configuration = configuration;
        this.dataBucket = configuration.getDataBucket();
        this.backupHandler = new BackupHandler(configuration, dataBucket);
    }

    public void handle(Message message, SocketChannel socket, SSLEngine engine, String address) throws Exception {
        Logger.debug(message, address);

        Message response = null;

        switch(message.getMessageType()) {
            case LOOKUP:
                Logger.debug(configuration.getChord().getSelf(), "Received LOOKUP of key " + message.getFileKey());
                ChordNode node = configuration.getChord().lookup(message.getFileKey()).get();
                if (node == null) break;
                Logger.debug(configuration.getChord().getSelf(), "Replying with " + node.toString());

                response = MessageFactory.getLookupResponseMessage(configuration.getPeerId(), message.getFileKey(), node);
                break;

            case GETPREDECESSOR:
                Logger.debug(configuration.getChord().getSelf(), "Received GETPREDECESSOR");
                ChordNode predecessorNode = configuration.getChord().getPredecessor();
                Logger.debug(configuration.getChord().getSelf(), "Replying with " + predecessorNode);

                response = MessageFactory.getNodeMessage(configuration.getPeerId(), predecessorNode);
                break;

            case GETSUCCESSOR:
                Logger.debug(configuration.getChord().getSelf(), "Received GETSUCCESSOR");
                ChordNode successorNode = configuration.getChord().getSuccessor();
                Logger.debug(configuration.getChord().getSelf(), "Replying with " + successorNode);

                response = MessageFactory.getNodeMessage(configuration.getPeerId(), successorNode);
                break;

            case NOTIFY:
                Logger.debug(configuration.getChord().getSelf(), "Received NOTIFY");
                configuration.getChord().notify(message.getNode());
                break;

            case PUTFILE:
                response = backupHandler.handle(message);
                break;

            case DATA:
                Logger.debug(DebugType.FILETRANSFER, "Received DATA: " + message);

                dataBucket.add(message.getFileKey(), message.getOrder(), message.getBody());
                response = MessageFactory.getProcessedYesMessage(configuration.getPeerId());
                break;

            case GETFILE:
                Logger.debug(DebugType.RESTORE, "Received GETFILE: " + message);

                if (!configuration.getPeerState().hasBackedUpFile(message.getFileKey()))
                {
                    Logger.debug(DebugType.RESTORE, "I don't have the file!");
                    if (configuration.getPeerState().isPointerFile(message.getFileKey())) response = MessageFactory.getRedirectMessage(configuration.getPeerId(), configuration.getChord().getSuccessor());
                    else response = MessageFactory.getProcessedNoMessage(configuration.getPeerId());
                    Logger.debug(DebugType.RESTORE, "Sending response " + response);
                }
                else
                {
                    FileManager manager = new FileManager(configuration.getRootDir());
                    FileSender.sendFile(configuration, new FileRepresentation(message.getFileKey(), manager.readBackedUpFile(message.getFileKey())), message.getNode());
                    response = MessageFactory.getProcessedYesMessage(configuration.getPeerId());
                }
                break;

            case REMOVEPOINTER:
                Logger.debug(DebugType.FILEPOINTER, "Removed pointer (waspointer=" + configuration.getPeerState().isPointerFile(message.getFileKey()) + ") for file " + message.getFileKey());
                configuration.getPeerState().removePointerFile(message.getFileKey());
                break;

            case ADDPOINTER:
                Logger.debug(DebugType.FILEPOINTER, "Added pointer (waspointer=" + configuration.getPeerState().isPointerFile(message.getFileKey()) + ") for file " + message.getFileKey());
                configuration.getPeerState().addPointerFile(message.getFileKey());
                break;

            case CHECK:
                ChordNode sender = message.getNode();
                int selfId = configuration.getPeerId();

                StringBuilder builder = new StringBuilder();
                builder.append("%% Check of ").append(sender).append(" by ").append(selfId).append(" %%\nMissing files:\n");

                for (int fileKey : configuration.getPeerState().getFilePointers()) {
                    if (Chord.isBetween(sender.getId(), fileKey, selfId, false)) {  // if the sender is a successor of the file and is before this node
                        SSLClient.sendQueued(configuration, sender, MessageFactory.getAddPointerMessage(selfId, fileKey), false);
                        builder.append("\t").append(fileKey).append(" (i have pointer)\n");
                    }
                }
                for (OthersFileInfo file : configuration.getPeerState().getOthersFiles()) {
                    if (Chord.isBetween(sender.getId(), file.getFileKey(), selfId, false)) { // if the sender is a successor of the file and is before this node
                        SSLClient.sendQueued(configuration, sender, MessageFactory.getAddPointerMessage(selfId, file.getFileKey()), false);
                        builder.append("\t").append(file.getFileKey()).append(" (i backed up)\n");
                    }
                }
                builder.append("\nFiles to be deleted:\n");
                for (int fileKey : configuration.getPeerState().getDeletedFiles()) {
                    if (Chord.isBetween(sender.getId(), fileKey, selfId, false)) { // if the sender is a successor of the file and is before this node
                        SSLClient.sendQueued(configuration, sender, MessageFactory.getDeleteMessage(selfId, fileKey), false);
                        builder.append("\t").append(fileKey).append("\n");
                    }
                }
                Logger.debug(DebugType.CHECK, builder.toString());
                break;

            case DELETE:
                Logger.debug(DebugType.DELETE, "Received delete for file = " + message.getFileKey());
                break;

            default:
                Logger.log("Received " + message.getMessageType());
                break;
        }

        if (response != null) {
            Logger.debug(DebugType.MESSAGE, "Sending response to client (" + response + ")");
            configuration.getServer().write(socket, engine, response.getBytes());
        }
    }
}
