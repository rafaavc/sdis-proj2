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

    public static Message getGetSuccessorMessage(int senderId) {
        return new Message(MessageType.GETSUCCESSOR, senderId);
    }

    public static Message getNodeMessage(int senderId, ChordNode node) {
        return new Message(MessageType.NODE, senderId, node);
    }

    public static Message getNotifyMessage(int senderId, ChordNode node) {
        return new Message(MessageType.NOTIFY, senderId, node);
    }

    public static Message getPutfileMessage(int senderId, int fileKey, int nParts, int replicationDeg, int alreadyObtainedDeg, int byteAmount) {
        return new Message(MessageType.PUTFILE,
                            senderId,
                            fileKey,
                            nParts,
                            replicationDeg,
                            alreadyObtainedDeg,
                            byteAmount);
    }

    public static Message getDataMessage(int senderId, int fileKey, int order, byte[] body) {
        return new Message(MessageType.DATA,
                                senderId,
                                fileKey,
                                order,
                                body);
    }

    public static Message getProcessedNoMessage(int senderId) {
        return new Message(MessageType.PROCESSEDNO, senderId);
    }

    public static Message getProcessedYesMessage(int senderId) {
        return new Message(MessageType.PROCESSEDYES, senderId);
    }

    public static Message getRedirectMessage(int senderId, ChordNode whereTo) {
        return new Message(MessageType.REDIRECT, senderId, whereTo);
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

    public static Message getGetfileMessage(int senderId, int fileKey, ChordNode node) throws ArgsException {
        return new Message(MessageType.GETFILE,
                            senderId,
                            fileKey,
                            node);
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
