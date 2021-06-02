package utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import chord.ChordNode;
import messages.Message;

public class Logger {
    public enum DebugType {
        SSL,
        MESSAGE,
        CHORD,
        QUEUE,
        BACKUP,
        FILEBUCKET,
        FILETRANSFER,
        RESTORE,
        RECLAIM,
        FILEPOINTER,
        CHECK,
        DELETE
    }

    private static final List<DebugType> active = new ArrayList<>();
    static {
//        setActive(DebugType.SSL);
//        setActive(DebugType.MESSAGE);
//        setActive(DebugType.CHORD);
//        setActive(DebugType.QUEUE);
//        setActive(DebugType.BACKUP);
//        setActive(DebugType.FILEBUCKET);
//        setActive(DebugType.FILETRANSFER);
//        setActive(DebugType.RESTORE);
//        setActive(DebugType.RECLAIM);
//        setActive(DebugType.FILEPOINTER);
//        setActive(DebugType.CHECK);
        setActive(DebugType.DELETE);
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
    
    public static void error(String doing, Throwable thrown, boolean showStackTrace) {
        System.err.println("WAS THROWN while " + doing + ": " + thrown.getMessage());
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

    public static void debug(Message message, String address) {
        if (!active.contains(DebugType.MESSAGE)) return;
        System.out.println("[MESSAGE](from " + address + "):\n" + message.toString());
    }

    public static void debug(DebugType debugType, String message) {
        if (debugType == DebugType.CHORD) {
            Logger.error("CHORD debug should go to Logger.debug(ChordNode node, String message)");
            return;
        }
        if (!active.contains(debugType)) return;
        System.out.println("[" + debugType + "] " + message);
    }

    public static void debug(ChordNode node, String message) {
        if (!active.contains(DebugType.CHORD)) return;
        System.out.println("[" + DebugType.CHORD + "@" + node.getId() + "] " + message);
    }

    public static void log(Object obj) {
        System.out.println(obj);
    }

    public static void todo(Object obj) {
        System.out.println("[TODO] " + obj.getClass().getName());
    }
}
