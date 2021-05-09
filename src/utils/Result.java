package utils;

import java.io.Serializable;

public class Result implements Serializable {
    private static final long serialVersionUID = -1997059718009930688L;
    private final boolean success;
    private final String message;
    
    public Result(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean success() {
        return success;
    }
}
