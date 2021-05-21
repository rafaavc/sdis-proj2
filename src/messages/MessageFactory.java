package messages;

import configuration.ProtocolVersion;
import exceptions.ArgsException;
import messages.Message.MessageType;

public class MessageFactory {
    private final ProtocolVersion version;
    
    public MessageFactory(ProtocolVersion version) throws ArgsException {
        this.version = version;
    }

    public Message getLookupMessage(int senderId, int key) {
        Message msg = new Message(version,
                                    MessageType.LOOKUP,
                                    senderId,
                                    String.valueOf(key));
        return msg;
    }

    public Message getPredecessorMessage(int senderId) {
        Message msg = new Message(version, MessageType.GETPREDECESSOR, senderId);
        return msg;
    }

    public Message notifyPredecessorMessage(int senderId) {
        Message msg = new Message(version, MessageType.NOTIFYPREDECESSOR, senderId);
        return msg;
    }

    public Message checkMessage(int senderId) {
        Message msg = new Message(version, MessageType.CHECK, senderId);
        return msg;
    }

    public byte[] getPutchunkMessage(int senderId, String key, int replicationDeg, int chunkNo, byte[] body) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.PUTCHUNK,
                                    senderId,
                                    key,
                                    chunkNo,
                                    replicationDeg,
                                    body);
        return msg.getBytes();
    }

    public byte[] getStoredMessage(int senderId, String fileId, int chunkNo) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.STORED,
                                    senderId,
                                    fileId,
                                    chunkNo);
        return msg.getBytes();
    }

    public byte[] getDeleteMessage(int senderId, String fileId) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.DELETE,
                                    senderId,
                                    fileId);
        return msg.getBytes();
    }

    public byte[] getGetchunkMessage(int senderId, String fileId, int chunkNo) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.GETCHUNK,
                                    senderId,
                                    fileId,
                                    chunkNo);
        return msg.getBytes();
    }

    public byte[] getChunkMessage(int senderId, String fileId, int chunkNo, byte[] body) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.CHUNK,
                                    senderId,
                                    fileId,
                                    chunkNo,
                                    body);
        return msg.getBytes();
    }

    public byte[] getRemovedMessage(int senderId, String fileId, int chunkNo) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.REMOVED,
                                    senderId,
                                    fileId,
                                    chunkNo);
        return msg.getBytes();
    }

    public byte[] getFilecheckMessage(int senderId, String fileId) throws ArgsException {
        Message msg = new Message(version, 
                                    MessageType.FILECHECK,
                                    senderId,
                                    fileId);
        return msg.getBytes();
    }
}
