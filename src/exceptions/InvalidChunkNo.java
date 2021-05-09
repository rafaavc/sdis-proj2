package exceptions;

public class InvalidChunkNo extends Exception {
    private static final long serialVersionUID = -1370669807080488430L;

    public InvalidChunkNo(String message) {
        super(message);
    }
}
