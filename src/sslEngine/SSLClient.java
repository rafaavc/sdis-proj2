package sslEngine;

import utils.Logger;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class SSLClient extends SSLPeer{

    private String address;
    private int port;
    private SSLEngine engine;
    private SocketChannel socket;

    public SSLClient(String protocol, String address, int port) throws Exception {
        this.address = address;
        this.port = port;

        SSLContext context = SSLContext.getInstance(protocol);
        context.init(createKeyManagers("./src/sslEngine/keys/client.keys", "storepass", "keypass"), createTrustManagers("./src/sslEngine/keys/truststore", "storepass"), new SecureRandom());
        this.engine = context.createSSLEngine(address, port);
        this.engine.setUseClientMode(true);

        SSLSession session = this.engine.getSession();
        this.appData = ByteBuffer.allocate(1024);  //might need to change
        this.netData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.peerAppData = ByteBuffer.allocate(1024);  //might need to change
        this.peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
    }


    @Override
    protected void read(SocketChannel socket, SSLEngine engine) throws Exception {
        Logger.log("Going to read from the server...");
        Logger.log("Going to read from the server...");

        this.peerNetData.clear();
        boolean read = true;
        while(read){
            int bytesRead = this.socket.read(this.peerNetData);
            if(bytesRead > 0){
                this.peerNetData.flip();
                while(this.peerNetData.hasRemaining()){
                    this.peerAppData.clear();
                    SSLEngineResult result = engine.unwrap(this.peerNetData, this.peerAppData);
                    switch (result.getStatus()){
                        case OK:
                            this.peerAppData.flip();
                            read = false;
                            break;
                        case CLOSED:
                            this.closeConnection(socket, engine);
                            return;
                        case BUFFER_UNDERFLOW:
                            this.peerNetData = this.handleBufferUnderflow(engine,this.peerNetData);
                            break;
                        case BUFFER_OVERFLOW:
                            this.peerAppData = this.enlargeApplicationBuffer(engine, this.peerAppData);
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                }
            }
            else if(bytesRead < 0){
                this.handleEndOfStream(socket, engine);
                return;
            }
        }

        Thread.sleep(150);
    }

    @Override
    protected void write(SocketChannel socket, SSLEngine engine, String message) throws Exception {
        Logger.log("Going to write to the server...");

        this.appData.clear();
        this.appData.put(message.getBytes());
        this.appData.flip();
        while(this.appData.hasRemaining()){
            this.netData.clear();
            SSLEngineResult result = engine.wrap(this.appData, this.netData);
            switch (result.getStatus()){
                case OK:
                    this.netData.flip();
                    while (this.netData.hasRemaining()){
                        socket.write(this.netData);
                    }
                    Logger.log("Sent to the client " + message);
                    break;
                case CLOSED:
                    this.closeConnection(socket, engine);
                    return;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow occured after a wrap.");
                case BUFFER_OVERFLOW:
                    this.netData = this.enlargePacketBuffer(engine, this.netData);
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
        }
    }

    public boolean connect() throws IOException {
        this.socket = SocketChannel.open();
        this.socket.configureBlocking(false);
        this.socket.connect(new InetSocketAddress(this.address, this.port));

        this.engine.beginHandshake();
        return this.executeHandshake(socket, this.engine);
    }

    public void write(String message) throws Exception {
        write(this.socket, this.engine, message);
    }

    public void read() throws Exception {
        read(this.socket, this.engine);
    }

    public void shutdown() throws IOException {
        Logger.log("Going to close connection with the server...");
        closeConnection(this.socket, this.engine);
        this.executor.shutdown();
    }
}
