package messages;

import chord.ChordNode;
import configuration.ProtocolVersion;
import exceptions.ArgsException;
import messages.Message.MessageType;

public class MessageFactory {
    private final ProtocolVersion version;
    
    public MessageFactory(ProtocolVersion version) throws ArgsException {
        this.version = version;
    }

    public Message getLookupMessage(int senderId, int key) {
        return new Message(version,
                                    MessageType.LOOKUP,
                                    senderId,
                                    key);
    }

    public Message getLookupResponseMessage(int senderId, int fileKey, ChordNode node) {
        return new Message(version, MessageType.LOOKUPRESPONSE, senderId, fileKey, node);
    }

    public Message getPredecessorMessage(int senderId) {
        return new Message(version, MessageType.GETPREDECESSOR, senderId);
    }

    public Message getNotifyPredecessorMessage(int senderId) {
        return new Message(version, MessageType.NOTIFYPREDECESSOR, senderId);
    }

    public Message getCheckMessage(int senderId) {
        return new Message(version, MessageType.CHECK, senderId);
    }

    public byte[] getPutchunkMessage(int senderId, int fileKey, int replicationDeg, int chunkNo, byte[] body) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.PUTCHUNK,
                                    senderId,
                                    fileKey,
                                    chunkNo,
                                    replicationDeg,
                                    body);
        return msg.getBytes();
    }

    public byte[] getStoredMessage(int senderId, int fileKey, int chunkNo) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.STORED,
                                    senderId,
                                    fileKey,
                                    chunkNo);
        return msg.getBytes();
    }

    public byte[] getDeleteMessage(int senderId, int fileKey) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.DELETE,
                                    senderId,
                                    fileKey);
        return msg.getBytes();
    }

    public byte[] getGetchunkMessage(int senderId, int fileKey, int chunkNo) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.GETCHUNK,
                                    senderId,
                                    fileKey,
                                    chunkNo);
        return msg.getBytes();
    }

    public byte[] getChunkMessage(int senderId, int fileKey, int chunkNo, byte[] body) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.CHUNK,
                                    senderId,
                                    fileKey,
                                    chunkNo,
                                    body);
        return msg.getBytes();
    }

    public byte[] getRemovedMessage(int senderId, int fileKey, int chunkNo) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.REMOVED,
                                    senderId,
                                    fileKey,
                                    chunkNo);
        return msg.getBytes();
    }

    public byte[] getFilecheckMessage(int senderId, int fileKey) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.FILECHECK,
                                    senderId,
                                    fileKey);
        return msg.getBytes();
    }
}
