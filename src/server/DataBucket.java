package server;

import utils.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class DataBucket {
    private final ConcurrentHashMap<Integer, FileBucket> fileData = new ConcurrentHashMap<>();

    public void add(int fileKey, FileBucket bucket) {
        fileData.put(fileKey, bucket);
    }

    public void add(int fileKey, int portionNumeration, byte[] data) {
        if (!fileData.containsKey(fileKey)) {
            Logger.error("[DATABUCKET] fileData doesn't contain the received key!");
        }
        fileData.get(fileKey).add(portionNumeration, data);
    }
    
}
