package sslengine;

import utils.Logger;
import utils.Logger.DebugType;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SSLPeer {

    /* Executor for handshake tasks */
    protected ExecutorService executor = Executors.newSingleThreadExecutor();

    private Object wrapLock = new Object(), unwrapLock = new Object();

    protected void initContext(SSLContext context) throws Exception {
        context.init(createKeyManagers("../sslengine/keys/client.jks", "123456", "123456"), 
            createTrustManagers("../sslengine/keys/truststore.jks", "123456"), 
            new SecureRandom());
    }
    
    public void write(SocketChannel socket, SSLEngine engine, byte[] message) throws Exception {
        Logger.debug(DebugType.SSL, "Going to write to the client...");

        ByteBuffer appData = ByteBuffer.wrap(message);
        ByteBuffer netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        while (appData.hasRemaining()) {
            netData.clear();
            synchronized(wrapLock) {
                SSLEngineResult result = engine.wrap(appData, netData);

                switch (result.getStatus()) {
                    case OK:
                        netData.flip();

                        while (netData.hasRemaining())
                            socket.write(netData);

                        Logger.debug(DebugType.SSL, "Sent to the client " + new String(message));
                        
                        break;

                    case CLOSED:
                        this.closeConnection(socket, engine);
                        return;

                    case BUFFER_UNDERFLOW:
                        throw new SSLException("Buffer underflow occurred after a wrap.");

                    case BUFFER_OVERFLOW:
                        netData = this.increaseBufferSize(netData, engine.getSession().getPacketBufferSize());
                        break;

                    default:
                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
            }
        }
    }

    protected ReadResult read(SocketChannel socket, SSLEngine engine) throws Exception {
        Logger.debug(DebugType.SSL, "Going to read from the server...");

        ByteBuffer peerAppData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize()),
            peerNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        int bytesRead = -1;

        peerNetData.clear();

        synchronized(unwrapLock) {
            bytesRead = socket.read(peerNetData);

            if (bytesRead > 0) {
                peerNetData.flip();

                while (peerNetData.hasRemaining()) {
                    peerAppData.clear(); // ??
                    SSLEngineResult result = engine.unwrap(peerNetData, peerAppData);
                    switch (result.getStatus()) {
                        case OK:
                            peerAppData.flip(); // ??
                            // if (read) read = false;
                            break;

                        case CLOSED:
                            this.closeConnection(socket, engine);
                            return new ReadResult(bytesRead, peerAppData);

                        case BUFFER_UNDERFLOW:
                            peerNetData = this.processBufferUnderflow(engine, peerNetData);
                            break;

                        case BUFFER_OVERFLOW:
                            peerAppData = this.increaseBufferSize(peerAppData, engine.getSession().getApplicationBufferSize());
                            break;

                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                }
            }
            else if (bytesRead < 0) {
                this.processEndOfStream(socket, engine);
                return new ReadResult(bytesRead, peerAppData);
            }
        }

        peerAppData.flip();
        return new ReadResult(bytesRead, peerAppData);
    }

    protected boolean executeHandshake(SocketChannel socketChannel, SSLEngine engine) throws IOException {

        Logger.debug(DebugType.SSL, "Starting handshake");

        int bufferSize = engine.getSession().getApplicationBufferSize();
        
        ByteBuffer appData = ByteBuffer.allocate(bufferSize),
            netData = ByteBuffer.allocate(bufferSize),
            peerAppData = ByteBuffer.allocate(bufferSize),
            peerNetData = ByteBuffer.allocate(bufferSize);

        SSLEngineResult result;
        SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();

        while (status != SSLEngineResult.HandshakeStatus.FINISHED && status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (status) {
                case NEED_UNWRAP:
                    if (socketChannel.read(peerNetData) < 0) {
                        if (engine.isInboundDone() && engine.isOutboundDone())
                            return false;

                        try {
                            engine.closeInbound();
                        } catch(Exception e) {
                            Logger.error("closing inbound during handshake", e, true);
                        }

                        engine.closeOutbound();
                        status = engine.getHandshakeStatus();
                        break;
                    }
                    peerNetData.flip();
                    try {
                        result = engine.unwrap(peerNetData, peerAppData);
                        peerNetData.compact();
                        status = result.getHandshakeStatus();
                    }
                    catch (SSLException exception){
                        Logger.error("unwrapping during handshake", exception, true);
                        
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
                            peerNetData = this.processBufferUnderflow(engine, peerNetData);
                            break;

                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;

                case NEED_WRAP:
                    netData.clear();
                    try {
                        result = engine.wrap(appData, netData);
                        status = result.getHandshakeStatus();
                    }
                    catch (SSLException exception) {
                        Logger.error("wrapping during handshake", exception, true);

                        engine.closeOutbound();
                        status = engine.getHandshakeStatus();
                        break;
                    }

                    switch (result.getStatus()) {
                        case OK:
                            netData.flip();
                            while (netData.hasRemaining()){
                                socketChannel.write(netData);
                            }
                            break;

                        case CLOSED:
                            try {
                                netData.flip();
                                while (netData.hasRemaining())
                                    socketChannel.write(netData);

                                peerNetData.clear();
                            }
                            catch (Exception exception) {
                                Logger.error("writing to socket channel", exception, false);

                                status = engine.getHandshakeStatus();
                            }
                            break;

                        case BUFFER_UNDERFLOW:
                            throw new SSLException("Buffer underflow occurred after a wrap.");
                        
                        case BUFFER_OVERFLOW:
                            netData = this.increaseBufferSize(netData, engine.getSession().getPacketBufferSize());
                            break;

                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;

                case NEED_TASK:
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null)
                        executor.execute(task);

                    status = engine.getHandshakeStatus();
                    break;

                case FINISHED:
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + status);
            }
        }

        Logger.debug(DebugType.SSL, "Finalized handshake");

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
        synchronized(this)
        {
            engine.closeOutbound();
            executeHandshake(socketChannel, engine);
            socketChannel.close();
        }
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
