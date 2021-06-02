package sslengine;

import chord.ChordNode;
import messages.Message;
import messages.MessageParser;
import sslengine.queue.MessageQueue;
import utils.Logger;
import utils.Logger.DebugType;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;

public class SSLClient extends SSLPeer{

    private final String address;
    private final int port;
    private final SSLEngine engine;
    private SocketChannel socket;
    public final static MessageQueue queue = new MessageQueue();

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

    public static Future<Message> sendQueued(ChordNode node, Message message, boolean wantReply) {
        return runSendQueueTask((CompletableFuture<Message> future) -> {
            try {
                queue.push(node, message, wantReply ? future::complete : null);
            } catch (Exception e) {
                Logger.error("pushing message action to queue", e, true);
            }
        }, wantReply);
    }

    public static Future<Message> sendQueued(InetSocketAddress address, Message message, boolean wantReply) {
        return runSendQueueTask((CompletableFuture<Message> future) -> {
            try {
                queue.push(address, message, wantReply ? future::complete : null);
            } catch (Exception e) {
                Logger.error("pushing message action to queue", e, true);
            }
        }, wantReply);
    }

    public static Future<Message> runSendQueueTask(Consumer<CompletableFuture<Message>> consumer, boolean wantReply) {
        CompletableFuture<Message> future = new CompletableFuture<>();

        consumer.accept(future);

        if (!wantReply) future.complete(null);
        return future;
    }

    public void shutdown() throws IOException {
        Logger.debug(DebugType.SSL, "Going to close connection with the server...");
        this.closeConnection(this.socket, this.engine);
        this.executor.shutdown();
    }
}
