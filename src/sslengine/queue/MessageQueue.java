package sslengine.queue;

import configuration.PeerConfiguration;
import messages.Message;
import sslengine.SSLClient;
import utils.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class MessageQueue {
    private final ConcurrentLinkedQueue<MessageAction> queue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(10);

    public MessageQueue() {
        executor.scheduleWithFixedDelay(this::sweep, 500, 500, TimeUnit.MILLISECONDS);
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

    public void push(InetSocketAddress address, Message message) throws Exception {
        push(address, message, null);
    }

    public void push(InetSocketAddress address, Message message, Consumer<Message> onComplete) throws Exception {
        SSLClient client = new SSLClient(address.getAddress().getHostAddress(), address.getPort());
        client.connect();

        queue.add(new MessageAction(message, client, (Message reply) -> {
            if (onComplete != null) onComplete.accept(reply);
            try {
                client.shutdown();
            } catch (Exception e) {
                Logger.error("handling message queue on complete for message " + message + " to address " + address, e, true);
            }
        }, onComplete != null));
    }

    public void destroy() throws InterruptedException {
        if (!executor.awaitTermination(2, TimeUnit.SECONDS))
            executor.shutdown();
    }



}
