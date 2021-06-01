package messages;

import chord.ChordNode;
import exceptions.ArgsException;
import messages.Message.MessageType;

public class MessageFactory {

    public static Message getLookupMessage(int senderId, int key) {
        return new Message(MessageType.LOOKUP,
                            senderId,
                            key);
    }

    public static Message getLookupResponseMessage(int senderId, int fileKey, ChordNode node) {
        return new Message(MessageType.LOOKUPRESPONSE, senderId, fileKey, node);
    }

    public static Message getGetPredecessorMessage(int senderId) {
        return new Message(MessageType.GETPREDECESSOR, senderId);
    }

    public static Message getPredecessorMessage(int senderId, ChordNode node) {
        return new Message(MessageType.PREDECESSOR, senderId, node);
    }

    public static Message getNotifyMessage(int senderId, ChordNode node) {
        return new Message(MessageType.NOTIFY, senderId, node);
    }

    public static Message getPutfileMessage(int senderId, int fileKey, int nParts, int replicationDeg) throws ArgsException {
        return new Message(MessageType.PUTFILE,
                            senderId,
                            fileKey,
                            nParts,
                            replicationDeg);
    }

    public static Message getDataMessage(int senderId, int fileKey, int order, byte[] body) {
        return new Message(MessageType.DATA,
                                senderId,
                                fileKey,
                                order,
                                body);
    }

    public static Message getProcessedMessage(int senderId) {
        return new Message(MessageType.PROCESSED, senderId);
    }

    public static byte[] getStoredMessage(int senderId, int fileKey, int chunkNo) throws ArgsException {
        Message msg = new Message(MessageType.STORED,
                                    senderId,
                                    fileKey,
                                    chunkNo);
        return msg.getBytes();
    }

    public static byte[] getDeleteMessage(int senderId, int fileKey) throws ArgsException {
        Message msg = new Message(MessageType.DELETE,
                                    senderId,
                                    fileKey);
        return msg.getBytes();
    }

    public static Message getGetfileMessage(int senderId, int fileKey, int chunkNo) throws ArgsException {
        return new Message(MessageType.GETFILE,
                            senderId,
                            fileKey,
                            chunkNo);
    }

    public static byte[] getChunkMessage(int senderId, int fileKey, int chunkNo, byte[] body) throws ArgsException {
        Message msg = new Message(MessageType.CHUNK,
                                    senderId,
                                    fileKey,
                                    chunkNo,
                                    body);
        return msg.getBytes();
    }

    public static byte[] getRemovedMessage(int senderId, int fileKey, int chunkNo) throws ArgsException {
        Message msg = new Message(MessageType.REMOVED,
                                    senderId,
                                    fileKey,
                                    chunkNo);
        return msg.getBytes();
    }

    public static byte[] getFilecheckMessage(int senderId, int fileKey) throws ArgsException {
        Message msg = new Message(MessageType.FILECHECK,
                                    senderId,
                                    fileKey);
        return msg.getBytes();
    }
}
