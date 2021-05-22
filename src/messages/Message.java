package messages;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import chord.ChordNode;
import configuration.ProtocolVersion;

public class Message {
    private final ProtocolVersion version;
    private final int senderId, fileKey;
    private ChordNode node = null;
    private MessageType messageType;
    private int chunkNo = -1;
    private short replicationDeg = -1;

    private byte[] body = null;

    public static enum MessageType {
        PUTCHUNK,
        STORED,
        GETCHUNK,
        CHUNK,
        DELETE,
        REMOVED,
        FILECHECK,
        LOOKUP,
        LOOKUPRESPONSE,
        GETPREDECESSOR,
        NOTIFYPREDECESSOR,
        CHECK
    }

    private static final String CRLF = new String(new byte[] { 0xD, 0xA });
    public static final HashMap<MessageType, String> messageTypeStrings = new HashMap<>();

    static {
        messageTypeStrings.put(MessageType.PUTCHUNK, "PUTCHUNK");
        messageTypeStrings.put(MessageType.STORED, "STORED");
        messageTypeStrings.put(MessageType.GETCHUNK, "GETCHUNK");
        messageTypeStrings.put(MessageType.CHUNK, "CHUNK");
        messageTypeStrings.put(MessageType.DELETE, "DELETE");
        messageTypeStrings.put(MessageType.REMOVED, "REMOVED");
        messageTypeStrings.put(MessageType.FILECHECK, "FILECHECK");
        messageTypeStrings.put(MessageType.LOOKUP, "LOOKUP");
        messageTypeStrings.put(MessageType.LOOKUPRESPONSE, "LOOKUPRESPONSE");
        messageTypeStrings.put(MessageType.GETPREDECESSOR, "GETPREDECESSOR");
        messageTypeStrings.put(MessageType.NOTIFYPREDECESSOR, "NOTIFYPREDECESSOR");
        messageTypeStrings.put(MessageType.CHECK, "CHECK");
    }

    public Message(ProtocolVersion version, int senderId, int fileKey) {
        this.version = version;
        this.senderId = senderId;
        this.fileKey = fileKey;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, int fileKey, ChordNode node) {
        this(version, senderId, fileKey);
        this.messageType = messageType;
        this.node = node;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId) {
        this(version, senderId, -1);
        this.messageType = messageType;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, int fileKey) {
        this(version, senderId, fileKey);
        this.messageType = messageType;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, int fileKey, int chunkNo) {
        this(version, messageType, senderId, fileKey);
        this.chunkNo = chunkNo;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, int fileKey, int chunkNo, int replicationDeg) {
        this(version, messageType, senderId, fileKey, chunkNo);
        this.replicationDeg = (short) replicationDeg;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, int fileKey, int chunkNo, byte[] body) {
        this(version, messageType, senderId, fileKey, chunkNo);
        this.body = body;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, int fileKey, int chunkNo, int replicationDeg, byte[] body) {
        this(version, messageType, senderId, fileKey, chunkNo, replicationDeg);
        this.body = body;
    }

    public Message setChunkNo(int chunkNo) {
        this.chunkNo = chunkNo;
        return this;
    }

    public Message setNode(String address, int port, int nodeId) throws UnknownHostException {
        this.node = new ChordNode(new InetSocketAddress(InetAddress.getByName(address), port), nodeId);
        return this;
    }

    public Message setMessageType(MessageType messageType) {
        this.messageType = messageType;
        return this;
    }

    public Message setReplicationDeg(short replicationDeg) {
        this.replicationDeg = replicationDeg;
        return this;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public byte[] getBody() throws Exception {
        if (this.body == null) throw new Exception("Trying to access body of message without this field.");
        return body;
    }

    public float getBodySizeKB() throws Exception {
        if (this.body == null) throw new Exception("Trying to access body of message without this field.");
        return (float) (body.length / 1000.);
    }

    public int getFileKey() {
        return fileKey;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public int getSenderId() {
        return senderId;
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public ChordNode getNode() throws Exception {
        if (this.node == null) throw new Exception("Trying to access node of message without this field.");
        return node;
    }

    public int getChunkNo() throws Exception {
        if (this.chunkNo < 0) throw new Exception("Trying to access chunkNo of message without this field.");
        return chunkNo;
    }

    public short getReplicationDeg() throws Exception {
        if (this.replicationDeg < 0) throw new Exception("Trying to access chunkNo of message without this field.");
        return replicationDeg;
    }

    @Override
    public String toString() {
        List<String> headerComponents = getComponents();

        StringBuilder builder = new StringBuilder();
        builder.append("Message:");

        for (String component : headerComponents) {
            builder.append(' ');
            builder.append(component);
        }

        return builder.toString();
    }

    private List<String> getComponents() {
        List<String> components = new ArrayList<>();
        components.add(version.toString());
        components.add(messageTypeStrings.get(messageType));
        components.add(String.valueOf(senderId));
        if (fileKey != -1) components.add(String.valueOf(fileKey));
        if (chunkNo != -1) components.add(String.valueOf(chunkNo));
        if (replicationDeg != -1) components.add(String.valueOf(replicationDeg));
        if (node != null) {
            components.add(node.getInetAddress().getHostAddress());
            components.add(String.valueOf(node.getPort()));
            components.add(String.valueOf(node.getId()));
        }
        return components;
    }

    public byte[] getBytes() {
        List<String> headerComponents = getComponents();

        StringBuilder builder = new StringBuilder();
        headerComponents.forEach((String el) -> {
            if (el != headerComponents.get(0)) builder.append(' ');  // comparing reference intentionally
            builder.append(el);
        });

        builder.append(' ' + CRLF + CRLF);
        String str =  builder.toString();
        byte[] header = str.getBytes();

        if (body != null) {
            byte[] data = new byte[header.length + body.length];

            System.arraycopy(header, 0, data, 0, header.length);
            System.arraycopy(body, 0, data, header.length, body.length);

            return data;
        }
        return header;
    }
}
