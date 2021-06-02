package messages;

import chord.ChordNode;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Message {
    private final int senderId;
    private final Integer fileKey;
    private ChordNode node = null;
    private MessageType messageType;
    private short replicationDeg = -1, alreadyPerceivedDegree = -1;
    private int order = -1;
    private int byteAmount = -1;

    private byte[] body = null;

    public enum MessageType {
        PUTFILE,
        GETFILE,
        DELETE,
        CHECK,
        LOOKUP,
        LOOKUPRESPONSE,
        GETPREDECESSOR,
        NODE,
        NOTIFY,
        DATA,
        PROCESSEDNO,   // processed ok, doesn't need to receive data
        PROCESSEDYES,   // processed ok, needs to receive data
        REDIRECT,
        GETSUCCESSOR,
        REMOVEPOINTER,
        ADDPOINTER
    }

    private static final String CRLF = new String(new byte[] { 0xD, 0xA });

    public Message(int senderId, Integer fileKey) {
        this.senderId = senderId;
        this.fileKey = fileKey;
    }

    public Message(int senderId) {
        this(senderId, null);
    }

    public Message(MessageType messageType, int senderId, int fileKey, ChordNode node) {
        this(senderId, fileKey);
        this.messageType = messageType;
        this.node = node;
    }

    public Message(MessageType messageType, int senderId, ChordNode node) {
        this(senderId);
        this.messageType = messageType;
        this.node = node;
    }

    public Message(MessageType messageType, int senderId) {
        this(senderId, -1);
        this.messageType = messageType;
    }

    public Message(MessageType messageType, int senderId, int fileKey) {
        this(senderId, fileKey);
        this.messageType = messageType;
    }

    public Message(MessageType messageType, int senderId, int fileKey, int replicationDeg) {
        this(messageType, senderId, fileKey);
        this.replicationDeg = (short) replicationDeg;
    }

    public Message(MessageType messageType, int senderId, int fileKey, int replicationDegree, byte[] body) {
        this(messageType, senderId, fileKey, replicationDegree);
        this.body = body;
    }

    public Message(MessageType messageType, int senderId, int fileKey, int nParts, int replicationDegree) {
        this(messageType, senderId, fileKey, replicationDegree);
        this.order = nParts;
    }

    public Message(MessageType messageType, int senderId, int fileKey, int nParts, int replicationDegree, int byteAmount) {
        this(messageType, senderId, fileKey, replicationDegree);
        this.order = nParts;
        this.byteAmount = byteAmount;
    }

    public Message setNode(String address, int port, int nodeId) throws UnknownHostException {
        this.node = new ChordNode(new InetSocketAddress(InetAddress.getByName(address), port), nodeId);
        return this;
    }

    public Message setOrder(int order) {
        this.order = order;
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

    public Message setByteAmount(int byteAmount) {
        this.byteAmount = byteAmount;
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

    public int getFileKey() throws Exception {
        if (this.fileKey == null) throw new Exception("Trying to access fileKey of message without this field.");
        return fileKey;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public int getSenderId() {
        return senderId;
    }

    public ChordNode getNode() throws Exception {
        if (this.node == null) throw new Exception("Trying to access node of message without this field.");
        return node;
    }

    public short getReplicationDeg() throws Exception {
        if (this.replicationDeg < 0) throw new Exception("Trying to access replication degree of message without this field.");
        return replicationDeg;
    }

    public int getOrder() throws Exception {
        if (this.order < 0) throw new Exception("Trying to access order of message without this field.");
        return order;
    }

    public int getByteAmount() throws Exception {
        if (this.byteAmount < 0) throw new Exception("Trying to access byte amount of message without this field.");
        return byteAmount;
    }

    @Override
    public String toString() {
        List<String> headerComponents = getComponents();

        StringBuilder builder = new StringBuilder();
        builder.append("Message:");

        for (String component : headerComponents) {
            builder.append(' ').append(component);
        }

        if (body != null) builder.append(" (body=").append(body.length).append("B)");

        return builder.toString();
    }

    private List<String> getComponents() {
        List<String> components = new ArrayList<>();
        components.add(messageType.toString());
        components.add(String.valueOf(senderId));
        if (fileKey != null) components.add(String.valueOf(fileKey));
        if (order != -1) components.add(String.valueOf(order));
        if (replicationDeg != -1) components.add(String.valueOf(replicationDeg));
        if (alreadyPerceivedDegree != -1) components.add(String.valueOf(alreadyPerceivedDegree));
        if (byteAmount != -1) components.add(String.valueOf(byteAmount));
        if (node != null) {
            components.add(node.getInetAddress().getHostAddress());
            components.add(String.valueOf(node.getPort()));
            components.add(String.valueOf(node.getId()));
        } else if (messageType == MessageType.NODE) {
            components.add("NULL");
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

        builder.append(' ').append(CRLF).append(CRLF);
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
