package messages.trackers;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import state.PeerState;
import utils.Logger;

public class StoredTracker {
    private final ConcurrentMap<String, List<Integer>> storedCount = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Runnable> notifiers = new ConcurrentHashMap<>();
    private static final Queue<StoredTracker> storedTrackers = new ConcurrentLinkedQueue<>();
    private boolean active = true;

    public static StoredTracker getNewTracker() {
        StoredTracker tracker = new StoredTracker();
        storedTrackers.add(tracker);
        return tracker;
    }

    public static void addStoredCount(PeerState state, String fileId, int chunkNo, int peerId) {
        for (StoredTracker tracker : storedTrackers) {
            tracker.addStored(state, fileId, chunkNo, peerId);
        }
    }

    public static void removeTracker(StoredTracker tracker) {
        storedTrackers.remove(tracker);
        tracker.active = false;
    }

    private void addStored(PeerState state, String fileId, int chunkNo, int peerId) {
        if (!active) {
            Logger.error("called addStoredCount on inactive StoredTracker");
            System.exit(1);
        }
        String key = fileId + chunkNo;
        synchronized(storedCount) {
            if (this.storedCount.containsKey(key)) {
                List<Integer> peerList = this.storedCount.get(key);
                if (!peerList.contains(peerId)) peerList.add(peerId);
            } else {
                List<Integer> peerList = new ArrayList<Integer>();
                peerList.add(peerId);
                this.storedCount.put(key, peerList);
            }
            if (this.notifiers.containsKey(key)) {
                this.notifiers.get(key).run();
            }
        }
    }

    public void addNotifier(String fileId, int chunkNo, Runnable notifier) {
        notifiers.put(fileId + chunkNo, notifier);
    }

    public int getStoredCount(String fileId, int chunkNo) {
        if (!active) {
            Logger.error("called getStoredCount on inactive StoredTracker");
            System.exit(1);
        }
        synchronized (storedCount) {
            return this.storedCount.containsKey(fileId + chunkNo) ? this.storedCount.get(fileId + chunkNo).size() : 0;
        }
    }
}
