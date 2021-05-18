package sslEngine;

import utils.Logger;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SSLPeer {

    //peer's application data
    protected ByteBuffer appData;

    //peer's encrypted data
    protected ByteBuffer netData;

    //other peer's application data
    protected ByteBuffer peerAppData;

    //other peer's encrypted data
    protected  ByteBuffer peerNetData;

    protected ExecutorService executor = Executors.newSingleThreadExecutor();

    protected abstract void read(SocketChannel socket, SSLEngine engine) throws Exception;

    protected abstract void write(SocketChannel socket, SSLEngine engine, byte[] message) throws Exception;

    protected boolean executeHandshake(SocketChannel socketChannel, SSLEngine engine) throws IOException {

        Logger.log("Starting handshake");

        SSLEngineResult result;
        SSLEngineResult.HandshakeStatus status;

        int bufferSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer appData = ByteBuffer.allocate(bufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(bufferSize);
        this.netData.clear();
        this.peerNetData.clear();

        status = engine.getHandshakeStatus();
        while(status != SSLEngineResult.HandshakeStatus.FINISHED && status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING){
            switch (status){
                case NEED_UNWRAP:
                    if(socketChannel.read(this.peerNetData) < 0){
                        if(engine.isInboundDone() && engine.isOutboundDone())
                            return false;

                        engine.closeInbound();
                        engine.closeOutbound();
                        status = engine.getHandshakeStatus();
                        break;
                    }
                    this.peerNetData.flip();
                    try{
                        result = engine.unwrap(this.peerNetData, peerAppData);
                        this.peerNetData.compact();
                        status = result.getHandshakeStatus();
                    }
                    catch (SSLException exception){
                        engine.closeOutbound();
                        status = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()){
                        case OK:
                            break;
                        case CLOSED:
                            if (engine.isOutboundDone()) {
                                return false;
                            } else {
                                engine.closeOutbound();
                                status = engine.getHandshakeStatus();
                                break;
                            }
                        case BUFFER_OVERFLOW:
                            peerAppData = this.increaseBufferSize(peerAppData, engine.getSession().getApplicationBufferSize());
                            break;
                        case BUFFER_UNDERFLOW:
                            this.peerNetData = this.processBufferUnderflow(engine, this.peerNetData);
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;
                case NEED_WRAP:
                    this.netData.clear();
                    try{
                        result = engine.wrap(appData, this.netData);
                        status = result.getHandshakeStatus();
                    }
                    catch (SSLException exception){
                        engine.closeOutbound();
                        status = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()){
                        case OK:
                            this.netData.flip();
                            while (this.netData.hasRemaining()){
                                socketChannel.write(this.netData);
                            }
                            break;
                        case CLOSED:
                            try{
                                this.netData.flip();
                                while(this.netData.hasRemaining()){
                                    socketChannel.write(this.netData);
                                }
                                this.peerNetData.clear();
                            }
                            catch (Exception exception){
                                status = engine.getHandshakeStatus();
                            }
                            break;
                        case BUFFER_UNDERFLOW:
                            throw new SSLException("Buffer underflow occurred after a wrap.");
                        case BUFFER_OVERFLOW:
                            this.netData = this.increaseBufferSize(this.netData, engine.getSession().getPacketBufferSize());
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while((task = engine.getDelegatedTask()) != null){
                        executor.execute(task);
                    }
                    status = engine.getHandshakeStatus();
                    break;
                case FINISHED:
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + status);
            }
        }

        Logger.log("Finalized handshake");

        return true;
    }


    protected ByteBuffer increaseBufferSize(ByteBuffer buffer, int capacity) {
        if (capacity > buffer.capacity()) {
            buffer = ByteBuffer.allocate(capacity);
        } else {
            buffer = ByteBuffer.allocate(buffer.capacity() * 2);
        }
        return buffer;
    }

    protected ByteBuffer processBufferUnderflow(SSLEngine engine, ByteBuffer buffer) {
        if (engine.getSession().getPacketBufferSize() < buffer.limit()) {
            return buffer;
        } else {
            ByteBuffer replaceBuffer = this.increaseBufferSize(buffer, engine.getSession().getPacketBufferSize());
            buffer.flip();
            replaceBuffer.put(buffer);
            return replaceBuffer;
        }
    }

    protected void closeConnection(SocketChannel socketChannel, SSLEngine engine) throws IOException  {
        engine.closeOutbound();
        executeHandshake(socketChannel, engine);
        socketChannel.close();
    }

    protected void processEndOfStream(SocketChannel socketChannel, SSLEngine engine) throws IOException  {
        try{
            engine.closeInbound();
        }
        catch (Exception e){
            Logger.error("This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream.");
        }
        closeConnection(socketChannel, engine);
    }

    protected KeyManager[] createKeyManagers(String filepath, String keystorePassword, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        InputStream keyStoreIS = new FileInputStream(filepath);
        try {
            keyStore.load(keyStoreIS, keystorePassword.toCharArray());
        } finally {
            keyStoreIS.close();

        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());
        return kmf.getKeyManagers();
    }

    protected TrustManager[] createTrustManagers(String filepath, String keystorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        InputStream trustStoreIS = new FileInputStream(filepath);
        try {
            trustStore.load(trustStoreIS, keystorePassword.toCharArray());
        } finally {
            trustStoreIS.close();
        }
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trustStore);
        return trustFactory.getTrustManagers();
    }
}
