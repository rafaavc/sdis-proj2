package sslengine;

import utils.Logger;
import utils.Logger.DebugType;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SSLPeer {

    /* Executor for handshake tasks */
    protected ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Object wrapLock = new Object(), unwrapLock = new Object();

    protected static void initContext(SSLContext context) throws Exception {
        context.init(createKeyManagers("../sslengine/keys/client.jks", "123456", "123456"), 
            createTrustManagers("../sslengine/keys/truststore.jks", "123456"), 
            new SecureRandom());
    }

    public static boolean isAlive(InetSocketAddress address) {
        try (Socket ignored = new Socket(address.getAddress().getHostAddress(), address.getPort())) {
            return true;
        } catch (Exception ignored) {}
        return false;
    }
    
    public void write(SocketChannel socket, SSLEngine engine, byte[] message) throws Exception {
        Logger.debug(DebugType.SSL, "Going to write to the client...");

        ByteBuffer appData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
        ByteBuffer netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

        int totalAmount = 0;
        while (totalAmount < message.length)
        {
            appData.clear();

            int amount = Math.min(appData.remaining(), message.length - totalAmount);

//            Logger.log("appData remaining = " + appData.remaining() + "sending " + amount + " bytes (" + (totalAmount + amount) + "/" + message.length + ")");

            appData.put(message, totalAmount, amount);
            appData.flip();

            totalAmount += amount;

            while (appData.hasRemaining()) {
                netData.clear();
                synchronized (wrapLock) {
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
                            netData = increaseBufferSize(netData, engine.getSession().getPacketBufferSize());
                            break;

                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                }
            }
        }
    }

    protected ReadResult read(SocketChannel socket, SSLEngine engine) throws Exception {
        Logger.debug(DebugType.SSL, "Going to read...");

        ByteBuffer peerAppData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize()),
            peerNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize() * 2);

        int bytesRead = -1;

        synchronized (unwrapLock) {
            bytesRead = socket.read(peerNetData);

//            Logger.log("Read " + bytesRead + " bytes");

            if (bytesRead > 0) {
                peerNetData.flip();

                while (peerNetData.hasRemaining()) {
                    peerAppData.clear(); // ??
                    SSLEngineResult result = engine.unwrap(peerNetData, peerAppData);

                    switch (result.getStatus()) {
                        case OK:
                            peerAppData.flip(); // ??
                            bytesRead = result.bytesProduced();
                            break;

                        case CLOSED:
                            this.closeConnection(socket, engine);
                            return new ReadResult(bytesRead, peerAppData);

                        case BUFFER_UNDERFLOW:
                            peerNetData = processBufferUnderflow(engine, peerNetData);
                            break;

                        case BUFFER_OVERFLOW:
                            peerAppData = increaseBufferSize(peerAppData, engine.getSession().getApplicationBufferSize());
                            break;

                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                }
            } else if (bytesRead < 0) {
                this.processEndOfStream(socket, engine);
                return new ReadResult(bytesRead, peerAppData);
            }
        }

        peerAppData.flip();
        return new ReadResult(bytesRead, peerAppData);
    }

    protected boolean executeHandshake(SocketChannel socketChannel, SSLEngine engine) throws IOException {
        return executeHandshake(executor, socketChannel, engine);
    }

    protected static boolean executeHandshake(Executor executor, SocketChannel socketChannel, SSLEngine engine) throws IOException {

        Logger.debug(DebugType.SSL, "Starting handshake");
        
        ByteBuffer appData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize()),
            netData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize()),
            peerAppData = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize()),
            peerNetData = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());

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
//                            Logger.error("closing inbound during handshake", e, true);
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
                            peerAppData = increaseBufferSize(peerAppData, engine.getSession().getApplicationBufferSize());
                            break;

                        case BUFFER_UNDERFLOW:
                            peerNetData = processBufferUnderflow(engine, peerNetData);
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
                            netData = increaseBufferSize(netData, engine.getSession().getPacketBufferSize());
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

                default:
                    throw new IllegalStateException("Invalid SSL status: " + status);
            }
        }

        Logger.debug(DebugType.SSL, "Finalized handshake");

        return true;
    }


    protected static ByteBuffer increaseBufferSize(ByteBuffer buffer, int capacity) {
        if (capacity > buffer.capacity()) {
            return ByteBuffer.allocate(capacity);
        }
        return ByteBuffer.allocate(buffer.capacity() * 2);
    }

    protected static ByteBuffer processBufferUnderflow(SSLEngine engine, ByteBuffer buffer) {
        if (engine.getSession().getPacketBufferSize() < buffer.limit())
            return buffer;

        ByteBuffer replaceBuffer = increaseBufferSize(buffer, engine.getSession().getPacketBufferSize());
        buffer.flip();
        replaceBuffer.put(buffer);

        return replaceBuffer;
    }

    protected void closeConnection(SocketChannel socketChannel, SSLEngine engine) throws IOException {
        closeConnection(executor, socketChannel, engine);
    }

    protected static void closeConnection(Executor executor, SocketChannel socketChannel, SSLEngine engine) throws IOException  {
        engine.closeOutbound();
        executeHandshake(executor, socketChannel, engine);
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

    protected static KeyManager[] createKeyManagers(String filepath, String keystorePassword, String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream keyStoreIS = new FileInputStream(filepath)) {
            keyStore.load(keyStoreIS, keystorePassword.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());
        return kmf.getKeyManagers();
    }

    protected static TrustManager[] createTrustManagers(String filepath, String keystorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (InputStream trustStoreIS = new FileInputStream(filepath)) {
            trustStore.load(trustStoreIS, keystorePassword.toCharArray());
        }
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trustStore);
        return trustFactory.getTrustManagers();
    }
}
