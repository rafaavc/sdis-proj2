package sslengine;

import utils.Logger;
import utils.Logger.DebugType;

import javax.net.ssl.*;

import messages.Message;
import messages.MessageParser;
import server.Router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SSLServer extends SSLPeer {

    private boolean available;

    private SSLContext context;

    private Selector selector;

    private String address;

    private Router router;

    private int port;

    private ThreadPoolExecutor threadpool;

    public SSLServer(String address, int port, Router router) throws Exception {
        this("TLS", address, port, router);
    }

    public SSLServer(String protocol, String address, int port, Router router) throws Exception {
        this.address = address;
        this.port = port;
        this.router = router;

        this.context = SSLContext.getInstance(protocol);
        initContext(context);

        this.selector = SelectorProvider.provider().openSelector();

        ServerSocketChannel socket = ServerSocketChannel.open();
        socket.configureBlocking(false);
        socket.socket().bind(new InetSocketAddress(address, port));
        socket.register(this.selector, SelectionKey.OP_ACCEPT);

        this.available = true;
        this.threadpool = (ThreadPoolExecutor) Executors.newFixedThreadPool(25);
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public void start() throws IOException {

        Logger.debug(DebugType.SSL, "Server ready!");

        while(this.available){
            this.selector.select();
            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();

            while (selectedKeys.hasNext()) {
                SelectionKey key = selectedKeys.next();
                selectedKeys.remove();
                try {
                    if (!key.isValid())
                        continue;

                    if (key.isAcceptable())
                        this.accept(key);

                    else if (key.isReadable()) 
                    {
                        SocketChannel socket = (SocketChannel) key.channel();
                        SSLEngine engine = (SSLEngine) key.attachment();

                        String address = socket.getRemoteAddress().toString();

                        ReadResult msg;
                        try {
                            msg = read(socket, engine);
                        } catch (Exception e) {
                            Logger.error("reading in server", e, !e.getMessage().trim().equals("Tag mismatch!"));
                            continue;
                        }

                        threadpool.execute(() -> {
                            try 
                            {
                                    if (msg.getBytesRead() > 0) {

                                        try 
                                        {   
                                            Message message;
                                            try {
                                                message = MessageParser.parse(msg.getData().array(), msg.getBytesRead());
                                            } catch(Exception e) {
                                                return;
                                            }
                                            
                                            try {
                                                router.handle(message, socket, engine, address);
                                            }
                                            catch (ClosedChannelException e) {
                                                Logger.error("The channel was closed! The message was " + message.getMessageType() + " Probably because peer didn't care about the answer.");
                                            }
                                        }
                                        catch (Exception e)
                                        {
                                            Logger.error("handling server request", e, true);
                                        }
                                    }

                                    // if (msg.getBytesRead() != 0) read = false;
                                //}
                            }
                            catch (Exception e)
                            {
                                Logger.error("processing key", e, true);
                            }
                        });
                    }
                } 
                catch (Exception e) 
                {
                    Logger.log("Got exception " + e.getMessage());
                    Logger.error(e, true);
                }
            }
        }

        Logger.debug(DebugType.SSL, "Server stopped!");
    }

    public void stop(){
        this.available = false;
        executor.shutdown();
        try {
            if (!threadpool.awaitTermination(2, TimeUnit.SECONDS)) threadpool.shutdown();
        } catch (InterruptedException e) {
            Logger.error("waiting for threadpool termination");
            threadpool.shutdown();
        }
        selector.wakeup();
    }

    public void accept(SelectionKey key) throws IOException {
        Logger.debug(DebugType.SSL, "New connection on hold!");

        SocketChannel socket = ((ServerSocketChannel) key.channel()).accept();
        socket.configureBlocking(false);

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.beginHandshake();

        if (this.executeHandshake(socket, engine))
            socket.register(selector, SelectionKey.OP_READ, engine);
        else {
            socket.close();
            Logger.error("Couldn't connect due to a handshake failure!");  // TODO maybe throw exception?
        }
    }
}

