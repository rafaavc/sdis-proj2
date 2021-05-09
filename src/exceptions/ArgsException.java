package exceptions;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ArgsException extends Exception {
    private static final long serialVersionUID = -714327807018527511L;

    public static enum Type {
        ARGS_LENGTH,
        VERSION_NO,
        CHUNK_NO,
        REPLICATION_DEG,
        FILE_DOESNT_EXIST,
        MESSAGE_TYPE,
        UNKNOWN_VERSION_NO,
        PEER_ID
    }

    private static final ConcurrentMap<Type, String> messages = new ConcurrentHashMap<>();

    static {
        messages.put(Type.ARGS_LENGTH, "Wrong amount of program arguments.");
        messages.put(Type.VERSION_NO, "Invalid version number. It should be <n>'.'<m>, where <n> and <m> are the ASCII codes of digits, between 0 and 9 (inclusive).");
        messages.put(Type.CHUNK_NO, "Invalid chunk no. Cannot exceed 6 chars.");
        messages.put(Type.REPLICATION_DEG, "Invalid replication degree. Must be a digit between 0 and 10 (both exclusive).");
        messages.put(Type.FILE_DOESNT_EXIST, "File doesn't exist.");
        messages.put(Type.MESSAGE_TYPE, "Message type not recognized.");
        messages.put(Type.UNKNOWN_VERSION_NO, "Unknown version number. Only know 1.0 (vanilla) and 1.1 (with enhancements).");
        messages.put(Type.PEER_ID, "Invalid peer ID. Must be an integer.");
    }

    public ArgsException(Type type) {
        super(messages.get(type));
    }

    public ArgsException(Type type, String extra) {
        super(messages.get(type) + " (" + extra + ")");
    }
}
