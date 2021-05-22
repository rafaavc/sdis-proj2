package sslengine;

import utils.Logger;
import utils.Logger.DebugType;

import javax.net.ssl.*;

import server.Router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.SecureRandom;
import java.util.Iterator;

public class SSLServer extends SSLPeer {

    private boolean available;

    private SSLContext context;

    private Selector selector;

    private String address;

    private Router router;

    private int port;

    public SSLServer(String address, int port, Router router) throws Exception {
        this("TLS", address, port, router);
    }

    public SSLServer(String protocol, String address, int port, Router router) throws Exception {
        this.address = address;
        this.port = port;
        this.router = router;
        this.context = SSLContext.getInstance(protocol);
        //define path to store the key managers and trust managers
        this.context.init(createKeyManagers("../sslengine/keys/server.jks","123456", "123456"), createTrustManagers("../sslengine/keys/truststore.jks","123456"), new SecureRandom());

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

    protected void read(SocketChannel socket, SSLEngine engine) throws Exception {
        Logger.debug(DebugType.SSL, "Going to read from the client...");

        this.peerNetData.clear();
        int bytesRead = socket.read(this.peerNetData);
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
                        Logger.debug(DebugType.SSL, "Closing connection with client");
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
            router.handle(bytes, bytesRead, socket, engine);
        }
        else if(bytesRead < 0){
            Logger.debug(DebugType.SSL, "End of stream, going to close connection with client");
            this.processEndOfStream(socket, engine);
        }
    }

    @Override
    public void write(SocketChannel socket, SSLEngine engine, byte[] message) throws Exception {
        Logger.debug(DebugType.SSL, "Going to write to the client...");

        this.appData.clear();
        this.appData.put(message);
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
                    Logger.debug(DebugType.SSL, "Sent to the client " + message);
                    break;
                case CLOSED:
                    this.closeConnection(socket, engine);
                    return;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow occurred after a wrap.");
                case BUFFER_OVERFLOW:
                    this.netData = this.increaseBufferSize(this.netData, engine.getSession().getPacketBufferSize());
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
        }
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public void start() throws Exception {

        Logger.debug(DebugType.SSL, "Server ready!");

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
                    // submit a thread to the executor?
                    this.read((SocketChannel) key.channel(), (SSLEngine) key.attachment());
            }
        }

        Logger.debug(DebugType.SSL, "Server stopped!");
    }

    public void stop(){
        this.available = false;
        executor.shutdown();
        selector.wakeup();
    }

    public void accept(SelectionKey key) throws IOException {
        Logger.debug(DebugType.SSL, "New connection on hold!");

        SocketChannel socket = ((ServerSocketChannel) key.channel()).accept();
        socket.configureBlocking(false);

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.beginHandshake();

        if(this.executeHandshake(socket, engine))
            socket.register(selector, SelectionKey.OP_READ, engine);
        else{
            socket.close();
            Logger.error("Couldn't connect due to a handshake failure!");  // TODO maybe throw exception?
        }

    }
}

