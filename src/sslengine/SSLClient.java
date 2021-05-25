package sslengine;

import utils.Logger;
import utils.Logger.DebugType;

import javax.net.ssl.*;

import messages.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.SecureRandom;
import java.util.function.BiConsumer;

public class SSLClient extends SSLPeer{

    private String address;
    private int port;
    private SSLEngine engine;
    private SocketChannel socket;

    public SSLClient(String address, int port) throws Exception {
        this("TLS", address, port);
    }

    public SSLClient(String protocol, String address, int port) throws Exception {
        this.address = address;
        this.port = port;

        SSLContext context = SSLContext.getInstance(protocol);
        context.init(createKeyManagers("../sslengine/keys/client.jks", "123456", "123456"), createTrustManagers("../sslengine/keys/truststore.jks", "123456"), new SecureRandom());
        this.engine = context.createSSLEngine(address, port);
        this.engine.setUseClientMode(true);

        SSLSession session = this.engine.getSession();
        this.appData = ByteBuffer.allocate(64000);  //might need to change
        this.netData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.peerAppData = ByteBuffer.allocate(64000);  //might need to change
        this.peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
    }


    protected void read(SocketChannel socket, SSLEngine engine, BiConsumer<byte[], Integer> consumer) throws Exception {
        Logger.debug(DebugType.SSL, "Going to read from the server...");

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
                            this.peerNetData = this.processBufferUnderflow(engine,this.peerNetData);
                            break;
                        case BUFFER_OVERFLOW:
                            this.peerAppData = this.increaseBufferSize(this.peerAppData, engine.getSession().getApplicationBufferSize());
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                }

                byte[] bytes = new byte[this.peerAppData.remaining()];
                this.peerAppData.get(bytes);
                Logger.debug(DebugType.SSL, "[DEFAULT CLIENT READER] Received from server:\n" + new String(bytes));
                if (consumer != null) consumer.accept(bytes, bytesRead);
            }
            else if(bytesRead < 0){
                this.processEndOfStream(socket, engine);
                return;
            }
            Thread.sleep(150);
        }
    }

    public boolean connect() throws IOException{
        boolean loop;
        this.socket = SocketChannel.open();
        this.socket.configureBlocking(false);
        this.socket.connect(new InetSocketAddress(this.address, this.port));
        loop = this.socket.finishConnect();
        while(!loop){
            loop = this.socket.finishConnect();
        }
        this.engine.beginHandshake();
        return this.executeHandshake(socket, this.engine);
    }

    public void write(Message message) throws Exception {
        write(this.socket, this.engine, message.getBytes());
    }

    public void write(byte[] message) throws Exception {
        write(this.socket, this.engine, message);
    }


    public void read() throws Exception {
        read(null);
    }

    public void read(BiConsumer<byte[], Integer> consumer) throws Exception {
        read(this.socket, this.engine, consumer);
    }

    public void shutdown() throws IOException {
        Logger.debug(DebugType.SSL, "Going to close connection with the server...");
        this.closeConnection(this.socket, this.engine);
        this.executor.shutdown();
    }
}
