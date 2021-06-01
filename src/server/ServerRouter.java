package server;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;

import chord.ChordNode;
import configuration.PeerConfiguration;
import exceptions.ArgsException;
import files.FileManager;
import messages.Message;
import messages.MessageFactory;
import messages.MessageParser;
import server.handlers.BackupHandler;
import utils.Logger;
import utils.Logger.DebugType;

public class ServerRouter implements Router {
    
    private final PeerConfiguration configuration;
    private final DataBucket dataBucket;
    private final BackupHandler backupHandler;

    public ServerRouter(PeerConfiguration configuration) {
        this.configuration = configuration;
        this.dataBucket = new DataBucket();
        this.backupHandler = new BackupHandler(configuration, dataBucket);
    }

    public void handle(Message message, SocketChannel socket, SSLEngine engine, String address) throws Exception {
        Logger.debug(message, address);

        Message response = null;

        switch(message.getMessageType()) {
            case LOOKUP:
                Logger.debug(configuration.getChord().getSelf(), "Received LOOKUP of key " + message.getFileKey());
                ChordNode node = configuration.getChord().lookup(message.getFileKey()).get();
                Logger.debug(configuration.getChord().getSelf(), "Replying with " + node.toString());

                response = MessageFactory.getLookupResponseMessage(configuration.getPeerId(), message.getFileKey(), node);
                break;

            case GETPREDECESSOR:
                Logger.debug(configuration.getChord().getSelf(), "Received GETPREDECESSOR");
                ChordNode predecessorNode = configuration.getChord().getPredecessor();
                Logger.debug(configuration.getChord().getSelf(), "Replying with " + predecessorNode);

                response = MessageFactory.getPredecessorMessage(configuration.getPeerId(), predecessorNode);
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
