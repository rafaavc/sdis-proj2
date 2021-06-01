package utils;

public class ResultWithData<T> extends Result {
    private final T data;

    public ResultWithData(boolean success, String message, T data) {
        super(success, message);
        this.data = data;
        hasData = true;
    }

    public T getData() {
        return data;
    }
}
