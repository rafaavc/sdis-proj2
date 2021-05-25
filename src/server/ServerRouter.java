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

    public void handle(byte[] dataReceived, int length, SocketChannel socket, SSLEngine engine) throws Exception {
        Message message = MessageParser.parse(dataReceived, length);
        Logger.debug(message, socket.getRemoteAddress().toString());

        switch(message.getMessageType()) {
            case LOOKUP:
                Logger.debug(DebugType.CHORD, "Received LOOKUP of key " + message.getFileKey());
                ChordNode node = configuration.getChord().lookup(message.getFileKey()).get();
                Logger.debug(DebugType.CHORD, "Replying with " + node.toString());

                byte[] response = messageFactory.getLookupResponseMessage(configuration.getPeerId(), message.getFileKey(), node).getBytes();
                configuration.getServer().write(socket, engine, response);
                break;
            case GETPREDECESSOR:
                Logger.todo(this);
                break;
            case NOTIFYPREDECESSOR:
                Logger.todo(this);
                break;
            case CHECK:
                Logger.todo(this);
                break;
            default:
                Logger.log("Received " + message.getMessageType());
                break;
        }
    }
}
