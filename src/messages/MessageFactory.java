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

    public static Message getRemovePointerMessage(int senderId, int fileKey) {
        return new Message(MessageType.REMOVEPOINTER, senderId, fileKey);
    }

    public static Message getDeleteMessage(int senderId, int fileKey) throws ArgsException {
        return new Message(MessageType.DELETE,
                                    senderId,
                                    fileKey);
    }

    public static Message getGetfileMessage(int senderId, int fileKey, ChordNode node) throws ArgsException {
        return new Message(MessageType.GETFILE,
                            senderId,
                            fileKey,
                            node);
    }

    public static Message getCheckMessage(int senderId, ChordNode node) throws ArgsException {
        return new Message(MessageType.CHECK, senderId, node);
    }

    public static Message getAddPointerMessage(int senderId, int fileKey) {
        return new Message(MessageType.ADDPOINTER, senderId, fileKey);
    }
}
