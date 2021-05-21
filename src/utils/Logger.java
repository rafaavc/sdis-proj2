package utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import messages.Message;

public class Logger {
    public enum DebugType {
        SSL,
        MESSAGE
    }

    private static List<DebugType> active = new ArrayList<>();
    static {
        setActive(DebugType.SSL);
        setActive(DebugType.MESSAGE);
    }

    public static void setActive(DebugType type) {
        if (!active.contains(type)) active.add(type);
    }

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

    public static void debug(Message message) {
        if (!active.contains(DebugType.MESSAGE)) return;
        System.out.println("[MESSAGE] " + message.toString());
    }

    public static void debug(DebugType debugType, String message) {
        if (!active.contains(debugType)) return;
        System.out.println("[" + debugType + "] " + message);
    }

    public static void log(Object obj) {
        System.out.println(obj);
    }

    public static void todo(Object obj) {
        System.out.println("[TODO] " + obj.getClass().getName());
    }
}
