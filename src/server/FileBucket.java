package server;

import utils.Logger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class FileBucket {

    private final Map<Integer, FilePart> parts = new ConcurrentHashMap<>();
    private final int goal;
    private final Consumer<byte[]> onComplete;
    private final ScheduledFuture<?> future;

    public FileBucket(int goal, Consumer<byte[]> onComplete) {
        this.goal = goal;
        this.onComplete = onComplete;
        future = Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(this::check, 250, 250, TimeUnit.MILLISECONDS);
    }

    private byte[] assembleFile() {
        int size = 0;
        for (FilePart part : parts.values()) size += part.getAmount();

        byte[] file = new byte[size];

        int totalAmount = 0;
        for (int i = 1; i <= parts.size(); i++) {
            FilePart part = parts.get(i);
            System.arraycopy(part.getData(), 0, file, totalAmount, part.getAmount());
            totalAmount += part.getAmount();
        }

        return file;
    }

    private void check() {
        Logger.debug(Logger.DebugType.FILEBUCKET, "Checking parts (size=" + parts.size() + ")");
        if (parts.size() >= goal) {
            Logger.debug(Logger.DebugType.FILEBUCKET, "Reached goal!");
            onComplete.accept(assembleFile());
            future.cancel(false);

            if (parts.size() > goal) Logger.error("Got more parts than needed! (" + parts.size() + "/" + goal + ")");
        }
    }

    public void add(int partNumeration, byte[] data) {
        parts.put(partNumeration, new FilePart(data, data.length));
    }
}
