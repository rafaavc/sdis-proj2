package sslengine.queue;

import messages.Message;
import sslengine.SSLClient;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

public class MessageAction {
    private final Message message;
    private final SSLClient client;
    private final Consumer<Message> onComplete;
    private final boolean wantsReply;

    public MessageAction(Message message, SSLClient client, Consumer<Message> onComplete, boolean wantsReply) {
        this.message = message;
        this.client = client;
        this.onComplete = onComplete;
        this.wantsReply = wantsReply;
    }

    public Message getMessage() {
        return message;
    }

    public SSLClient getClient() {
        return client;
    }

    public boolean wantsReply() {
        return wantsReply;
    }

    public Consumer<Message> getOnComplete() {
        return onComplete;
    }

    public String toString() {
        return "ACTION[message=" + message + ", wantsReply=" + wantsReply() + "]";
    }
}
