package server;

import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;

import chord.ChordNode;
import configuration.PeerConfiguration;
import exceptions.ArgsException;
import messages.Message;
import messages.MessageFactory;
import messages.MessageParser;
import utils.Logger;
import utils.Logger.DebugType;

public class ServerRouter implements Router {
    
    private final PeerConfiguration configuration;
    private final MessageFactory messageFactory;

    public ServerRouter(PeerConfiguration configuration) throws ArgsException {
        this.configuration = configuration;
        this.messageFactory = new MessageFactory(configuration.getProtocolVersion());
    }

    public void handle(Message message, SocketChannel socket, SSLEngine engine, String address) throws Exception {
        Logger.debug(message, address);

        byte[] response = null;

        switch(message.getMessageType()) {
            case LOOKUP:
                Logger.debug(DebugType.CHORD, "Received LOOKUP of key " + message.getFileKey());
                ChordNode node = configuration.getChord().lookup(message.getFileKey()).get();
                Logger.debug(DebugType.CHORD, "Replying with " + node.toString());

                response = messageFactory.getLookupResponseMessage(configuration.getPeerId(), message.getFileKey(), node).getBytes();
                break;

            case GETPREDECESSOR:
                Logger.debug(DebugType.CHORD, "Received GETPREDECESSOR");
                ChordNode predecessorNode = configuration.getChord().getPredecessor();
                Logger.debug(DebugType.CHORD, "Replying with " + predecessorNode);

                response = messageFactory.getPredecessorMessage(configuration.getPeerId(), predecessorNode).getBytes();
                break;

            case NOTIFY:
                Logger.debug(DebugType.CHORD, "Received NOTIFY");
                configuration.getChord().notify(message.getNode());
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
