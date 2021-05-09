package messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import configuration.ProtocolVersion;

public class Message {
    private final ProtocolVersion version;
    private MessageType messageType;
    private final int senderId;
    private final String fileId;
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
        FILECHECK
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
    }

    public Message(ProtocolVersion version, int senderId, String fileId) {
        this.version = version;  // TODO verify version
        this.senderId = senderId;
        this.fileId = fileId;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, String fileId) {
        this(version, senderId, fileId);
        this.messageType = messageType;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, String fileId, int chunkNo) {
        this(version, messageType, senderId, fileId);
        this.chunkNo = chunkNo;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, String fileId, int chunkNo, int replicationDeg) {
        this(version, messageType, senderId, fileId, chunkNo);
        this.replicationDeg = (short) replicationDeg;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, String fileId, int chunkNo, byte[] body) {
        this(version, messageType, senderId, fileId, chunkNo);
        this.body = body;
    }

    public Message(ProtocolVersion version, MessageType messageType, int senderId, String fileId, int chunkNo, int replicationDeg, byte[] body) {
        this(version, messageType, senderId, fileId, chunkNo, replicationDeg);
        this.body = body;
    }

    public Message setChunkNo(int chunkNo) {
        this.chunkNo = chunkNo;
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

    public String getFileId() {
        return fileId;
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
        components.add(fileId);
        if (chunkNo != -1) components.add(String.valueOf(chunkNo));
        if (replicationDeg != -1) components.add(String.valueOf(replicationDeg));
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
