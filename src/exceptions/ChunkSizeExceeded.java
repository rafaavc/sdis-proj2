package exceptions;

public class ChunkSizeExceeded extends Exception {
    private static final long serialVersionUID = 8890771508457328666L;

    public ChunkSizeExceeded(String message) {
        super("Size exceeded: " + message + "bytes");
    }
}

