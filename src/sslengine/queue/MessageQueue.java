package sslengine.queue;

import chord.ChordNode;
import configuration.PeerConfiguration;
import messages.Message;
import sslengine.SSLClient;
import utils.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * This class assures that writes are sequential
 */
public class MessageQueue {
    private final ConcurrentLinkedQueue<MessageAction> queue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(10);
    private PeerConfiguration configuration = null;

    public MessageQueue() {
        executor.scheduleWithFixedDelay(this::sweep, 500, 500, TimeUnit.MILLISECONDS);
    }

    public void setConfiguration(PeerConfiguration configuration) {
        this.configuration = configuration;
    }

    public void sweep() {
        Logger.debug(Logger.DebugType.QUEUE, "Sweeping...");
        while (!queue.isEmpty()) {
            MessageAction action = queue.poll();
            Logger.debug(Logger.DebugType.QUEUE, "Doing action: " + action);
            try {
                SSLClient.send(scheduler, action.getClient(), action.getMessage(), action.getOnComplete(), action.wantsReply());
            } catch(Exception e) {
                Logger.error("handling message queue sweep for " + action, e, true);
            }
        }
        Logger.debug(Logger.DebugType.QUEUE, "Sweep complete!");
    }

    private void push(InetSocketAddress address, Message message, SSLClient client, Consumer<Message> onComplete) {
        queue.add(new MessageAction(message, client, (Message reply) -> {
            if (onComplete != null) onComplete.accept(reply);
            try {
                client.shutdown();
            } catch (Exception e) {
                Logger.error("handling message queue on complete for message " + message + " to address " + address, e, true);
            }
        }, onComplete != null));
    }

    public void push(ChordNode node, Message message, Consumer<Message> onComplete) throws Exception {
        SSLClient client = new SSLClient(node.getInetSocketAddress().getAddress().getHostAddress(), node.getInetSocketAddress().getPort());
        try {
            client.connect();
        } catch (IOException e) {
            Logger.error("connecting to peer in SSLClient.send", e, false);
            if (onComplete != null) onComplete.accept(null);
            if (configuration != null) configuration.getChord().peerIsDown(node);
            else Logger.error("Configuration is null when pushing to queue a ChordNode");
            return;
        }

        push(node.getInetSocketAddress(), message, client, onComplete);
    }

    public void push(InetSocketAddress address, Message message, Consumer<Message> onComplete) throws Exception {
        if (configuration != null) {
            Logger.error("Sending message without peer is down handling when configuration is not null!");
        }
        SSLClient client = new SSLClient(address.getAddress().getHostAddress(), address.getPort());
        try {
            client.connect();
        } catch (IOException e) {
            Logger.error("connecting to peer in SSLClient.send", e, false);
            if (onComplete != null) onComplete.accept(null);
            return;
        }

        push(address, message, client, onComplete);
    }

    public void destroy() {
        executor.shutdown();
    }
}
