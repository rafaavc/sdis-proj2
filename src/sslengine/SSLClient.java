package sslengine;

import utils.Logger;
import utils.Logger.DebugType;

import javax.net.ssl.*;

import configuration.PeerConfiguration;
import messages.Message;
import messages.MessageParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public class SSLClient extends SSLPeer{

    private final String address;
    private final int port;
    private final SSLEngine engine;
    private SocketChannel socket;

    public SSLEngine getEngine() {
        return engine;
    }

    public SocketChannel getSocket() {
        return socket;
    }

    public SSLClient(String address, int port) throws Exception {
        this("TLS", address, port);
    }

    public SSLClient(String protocol, String address, int port) throws Exception {
        this.address = address;
        this.port = port;

        SSLContext context = SSLContext.getInstance(protocol);
        initContext(context);

        this.engine = context.createSSLEngine(address, port);
        this.engine.setUseClientMode(true);
    }

    public boolean connect() throws IOException{
        this.socket = SocketChannel.open();
        this.socket.configureBlocking(false);
        this.socket.connect(new InetSocketAddress(this.address, this.port));

        boolean loop = this.socket.finishConnect();
        while (!loop) loop = this.socket.finishConnect();

        this.engine.beginHandshake();
        return this.executeHandshake(socket, this.engine);
    }

    public void write(Message message) throws Exception {
        write(this.socket, this.engine, message.getBytes());
    }

    public void write(byte[] message) throws Exception {
        write(this.socket, this.engine, message);
    }

    public Message sendAndReadReply(Message message) throws Exception {
        return sendAndReadReply(message, true);
    }

    public Message sendAndReadReply(Message message, boolean parseMessage) throws Exception {
        int total = 0;
        while (total < 5) {
            total++;
            write(message);

            int count = 0;
            while (true) {
                try {
                    ReadResult data = read(this.socket, this.engine);
                    if (parseMessage) return MessageParser.parse(data.getData().array(), data.getBytesRead());
                    else if (data.getBytesRead() <= 0) throw new Exception();
                    else return null;
                } catch (Exception e) {
                    count++;
                    if (count > 4) break; // couldn't read reply
                    Thread.sleep(count * 250);
                }
            }
        }
        if (parseMessage) throw new Exception("Couldn't get a reply to message: " + message.toString().trim());
        return null;
    }


    public void read() throws Exception {
        read(null);
    }

    public void read(BiConsumer<byte[], Integer> consumer) throws Exception {
        ReadResult msg = read(this.socket, this.engine);
        if (consumer != null) consumer.accept(msg.getData().array(), msg.getBytesRead());
    }

    public Message readReply() throws InterruptedException {
        int count = 0;
        while (true) {
            try
            {
                ReadResult data = read(socket, engine);
                if (data.getBytesRead() <= 0) throw new Exception();
                return MessageParser.parse(data.getData().array(), data.getBytesRead());
            } 
            catch (Exception e)
            {
                count++;
                if (count > 4) break; // couldn't read reply
                Thread.sleep(count * 250);
            }
        }
        return null;
    }

    public static Future<Message> sendAndGetReply(PeerConfiguration configuration, InetSocketAddress address, Message message) throws Exception {
        CompletableFuture<Message> future = new CompletableFuture<>();
        configuration.getThreadScheduler().submit(() -> {
            try {
                
                int total = 0;
                while (total < 5 && !future.isDone()) {
                    total++;
                
                    SSLClient client = new SSLClient(address.getAddress().getHostAddress(), address.getPort());
                    client.connect();

                    client.write(message);

                    Message reply = client.readReply();

                    client.shutdown();

                    if (reply != null) future.complete(reply);
                }

                if (!future.isDone()) throw new Exception("Couldn't get a reply to message: " + message.toString().trim());

            } catch(Exception e) {
                future.completeExceptionally(e);
            }

        });
        return future;
    }

    public void shutdown() throws IOException {
        Logger.debug(DebugType.SSL, "Going to close connection with the server...");
        this.closeConnection(this.socket, this.engine);
        this.executor.shutdown();
    }
}
