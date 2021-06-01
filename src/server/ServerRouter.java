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
import utils.Logger;
import utils.Logger.DebugType;

public class ServerRouter implements Router {
    
    private final PeerConfiguration configuration;
    private final DataBucket dataBucket = new DataBucket();

    public ServerRouter(PeerConfiguration configuration) throws ArgsException {
        this.configuration = configuration;
    }

    public void handle(Message message, SocketChannel socket, SSLEngine engine, String address) throws Exception {
        Logger.debug(message, address);

        byte[] response = null;

        switch(message.getMessageType()) {
            case LOOKUP:
                Logger.debug(configuration.getChord().getSelf(), "Received LOOKUP of key " + message.getFileKey());
                ChordNode node = configuration.getChord().lookup(message.getFileKey()).get();
                Logger.debug(configuration.getChord().getSelf(), "Replying with " + node.toString());

                response = MessageFactory.getLookupResponseMessage(configuration.getPeerId(), message.getFileKey(), node).getBytes();
                break;

            case GETPREDECESSOR:
                Logger.debug(configuration.getChord().getSelf(), "Received GETPREDECESSOR");
                ChordNode predecessorNode = configuration.getChord().getPredecessor();
                Logger.debug(configuration.getChord().getSelf(), "Replying with " + predecessorNode);

                response = MessageFactory.getPredecessorMessage(configuration.getPeerId(), predecessorNode).getBytes();
                break;

            case NOTIFY:
                Logger.debug(configuration.getChord().getSelf(), "Received NOTIFY");
                configuration.getChord().notify(message.getNode());
                break;

            case PUTFILE:
                Logger.debug(DebugType.BACKUP, "Received PUTFILE: " + message);
                dataBucket.add(message.getFileKey(), new FileBucket(message.getOrder(), (byte[] data) -> {
                    try {
                        new FileManager().write("test.jpeg", data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }));

                response = MessageFactory.getProcessedMessage(configuration.getPeerId()).getBytes();
                break;

            case DATA:
                Logger.debug(DebugType.BACKUP, "Received DATA: " + message);

                dataBucket.add(message.getFileKey(), message.getOrder(), message.getBody());
                break;


            default:
                Logger.log("Received " + message.getMessageType());
                break;
        }

        if (response != null) {
            Logger.debug(DebugType.MESSAGE, "Sending response to client: '" + new String(response).trim() + "'");
            configuration.getServer().write(socket, engine, response);
        }
    }
}
