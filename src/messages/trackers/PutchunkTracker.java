package messages.trackers;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import utils.Logger;

public class PutchunkTracker {
    private final Queue<String> putchunksReceived = new ConcurrentLinkedQueue<>();
    private static final Queue<PutchunkTracker> putchunkTrackers = new ConcurrentLinkedQueue<>();
    private boolean active = true;

    public static PutchunkTracker getNewTracker() {
        PutchunkTracker tracker = new PutchunkTracker();
        putchunkTrackers.add(tracker);
        return tracker;
    }

    public static void addPutchunkReceived(String fileId, int chunkNo) {
        for (PutchunkTracker tracker : putchunkTrackers) {
            tracker.addPutchunk(fileId, chunkNo);
        }
    }

    public static void removeTracker(PutchunkTracker tracker) {
        putchunkTrackers.remove(tracker);
        tracker.active = false;
    }

    public boolean hasReceivedPutchunk(String fileId, int chunkNo) {
        if (!active) {
            Logger.error("called hasReceivedPutchunk on inactive PutchunkTracker");
            System.exit(1);
        }
        return this.putchunksReceived.contains(fileId + chunkNo);
    }

    private void addPutchunk(String fileId, int chunkNo) {
        if (!active) {
            Logger.error("called addPutchunk on inactive PutchunkTracker");
            System.exit(1);
        }
        synchronized (putchunksReceived) {
            if (!this.putchunksReceived.contains(fileId + chunkNo)) this.putchunksReceived.add(fileId + chunkNo);
        }
    }
}
