package sslengine;

import sslengine.queue.MessageQueue;
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
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SSLClient extends SSLPeer{

    private final String address;
    private final int port;
    private final SSLEngine engine;
    private SocketChannel socket;
    public final static MessageQueue queue = new MessageQueue();

    public SSLEngine getEngine() {
        return engine;
    }

    public SocketChannel getSocket() {
        return socket;
    }

    public SSLClient(String address, int port) throws Exception {
        this("TLSv1.2", address, port);
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

    public void read() throws Exception {
        read(null);
    }

    public void read(BiConsumer<byte[], Integer> consumer) throws Exception {
        ReadResult msg = read(this.socket, this.engine);
        if (consumer != null) consumer.accept(msg.getData().array(), msg.getBytesRead());
    }

    public Message readReply(int maxCount) throws InterruptedException {
        int count = 0;
        int previousValue = 125;
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
                if (count > maxCount) break; // couldn't read reply
                previousValue *= 2;
                Thread.sleep(previousValue);  // TODO remove
            }
        }
        return null;
    }

//    public static Future<Message> send(PeerConfiguration configuration, InetSocketAddress address, Message message, boolean wantReply) throws Exception {
//        CompletableFuture<Message> future = new CompletableFuture<>();
//            try {
//
////                int total = 0;
////                while (total < 5 && !future.isDone()) {
////                    total++;
//
//                    SSLClient client = new SSLClient(address.getAddress().getHostAddress(), address.getPort());
//                    client.connect();
//
//                    client.write(message);
//                configuration.getThreadScheduler().submit(() -> {
//
//                    Message reply = null;
//                    if (wantReply) reply = client.readReply();
//
//                    client.shutdown();
//
//                    if (reply != null || !wantReply) future.complete(reply);
//
//                    if (!future.isDone()) throw new Exception("Couldn't get a reply to message: " + message.toString().trim());
//                });
//
//            } catch(Exception e) {
//                future.completeExceptionally(e);
//            }
//
//        return future;
//    }

    public static void send(ScheduledThreadPoolExecutor scheduler, SSLClient client, Message message, Consumer<Message> onComplete, boolean wantReply) throws Exception {
        client.write(message);

        scheduler.execute(() -> {
            try {
                Message reply = client.readReply(5);

                if (reply != null || !wantReply) {
                    onComplete.accept(reply);
                    return;
                }

                throw new Exception("Couldn't get a reply to message: " + message.toString().trim());
            } catch (Exception e) {
                Logger.error("reading reply to message in send method of SSLClient", e, false);
                onComplete.accept(null);
            }
        });
    }

    public static Future<Message> sendQueued(PeerConfiguration configuration, InetSocketAddress address, Message message, boolean wantReply) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        Runnable pushTask = () -> {
            try {
                queue.push(address, message, wantReply ? future::complete : null);
            } catch (Exception e) {
                Logger.error("pushing message action to queue", e, true);
            }
        };

        configuration.getThreadScheduler().execute(pushTask);

        if (!wantReply) future.complete(null);
        return future;
    }



    public void shutdown() throws IOException {
        Logger.debug(DebugType.SSL, "Going to close connection with the server...");
        this.closeConnection(this.socket, this.engine);
        this.executor.shutdown();
    }
}
