package utils;

import java.util.concurrent.CompletableFuture;

import channels.MulticastChannel;
import channels.MulticastChannel.ChannelType;
import messages.Message;

public class Logger {
    public static void error(Throwable thrown, boolean showStackTrace) {
        System.err.println("WAS THROWN: " + thrown.getMessage());
        if (showStackTrace) {
            thrown.printStackTrace();
        }
    }

    public static void error(Throwable thrown, CompletableFuture<Result> future) {
        System.err.println("WAS THROWN: " + thrown.getMessage());
        thrown.printStackTrace();
        future.complete(new Result(false, thrown.getMessage()));
        
    }

    public static void error(String msg) {
        System.err.println(msg);
    }

    public static void log(ChannelType type, Message msg) {
        System.out.println("[" + MulticastChannel.messages.get(type) + "] " + msg);
    }

    public static void log(Object obj) {
        System.out.println(obj);
    }
}
