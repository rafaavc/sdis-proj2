package sslEngine;

import utils.Logger;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;

public class SSLServer extends SSLPeer{

    private boolean available;

    private SSLContext context;

    private Selector selector;


    public SSLServer(String protocol, String address, int port) throws Exception {
        this.context = SSLContext.getInstance(protocol);
        //define path to store the key managers and trust managers
        this.context.init(createKeyManagers("./src/sslEngine/keys/server.keys","storepass", "keypass"), createTrustManagers("./src/sslEngine/keys/truststore","storepass"), new SecureRandom());

        SSLSession session = context.createSSLEngine().getSession();
        this.appData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.netData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        session.invalidate();

        selector = SelectorProvider.provider().openSelector();
        ServerSocketChannel socket = ServerSocketChannel.open();
        socket.configureBlocking(false);
        socket.socket().bind(new InetSocketAddress(address, port));
        socket.register(this.selector, SelectionKey.OP_ACCEPT);

        this.available = true;

    }

    @Override
    protected void read(SocketChannel socket, SSLEngine engine) throws Exception {
        Logger.log("Going to read from the client...");

        this.peerNetData.clear();
        int bytesRead = socket.read(this.peerAppData);
        if(bytesRead > 0){
            this.peerNetData.flip();
            while(this.peerNetData.hasRemaining()){
                this.peerAppData.clear();
                SSLEngineResult result = engine.unwrap(this.peerNetData, this.peerAppData);
                switch (result.getStatus()){
                    case OK:
                        this.peerAppData.flip();
                        break;
                    case CLOSED:
                        Logger.log("Closing connection with client");
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

            this.write(socket, engine, "I am your server");
        }
        else if(bytesRead < 0){
            Logger.log("End of stream, going to close connection with client");
            this.handleEndOfStream(socket, engine);
        }
    }

    @Override
    protected void write(SocketChannel socket, SSLEngine engine, String message) throws Exception {
        Logger.log("Going to write to the client...");

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

    public void start() throws Exception {

        Logger.log("Server ready!");

        while(this.available){
            this.selector.select();
            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
            while(selectedKeys.hasNext()){
                SelectionKey key = selectedKeys.next();
                selectedKeys.remove();
                if(!key.isValid())
                    continue;
                if(key.isAcceptable())
                    this.accept(key);
                else if(key.isReadable())
                    this.read((SocketChannel) key.channel(), (SSLEngine) key.attachment());
            }
        }

        Logger.log("Server stopped!");
    }

    public void stop(){
        this.available = false;
        executor.shutdown();
        selector.wakeup();
    }

    public void accept(SelectionKey key) throws IOException {
        Logger.log("New connection on hold!");

        SocketChannel socket = ((ServerSocketChannel) key.channel()).accept();
        socket.configureBlocking(false);

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.beginHandshake();

        if(this.executeHandshake(socket, engine))
            socket.register(selector, SelectionKey.OP_READ, engine);
        else{
            socket.close();
            Logger.log("Couldn't connect due to a handshake failure");
        }

    }
}
