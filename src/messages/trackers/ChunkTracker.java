package messages.trackers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import utils.Logger;

public class ChunkTracker {
    private final ConcurrentMap<String, List<Integer>> chunksReceived = new ConcurrentHashMap<>(); 
    private final ConcurrentMap<String, Map<Integer, byte[]>> chunksDataReceived = new ConcurrentHashMap<>();

    public synchronized void startWaitingForChunk(String fileId, int chunkNo) {
        if (!chunksDataReceived.containsKey(fileId)) chunksDataReceived.put(fileId, new HashMap<Integer, byte[]>());
        if (!chunksDataReceived.get(fileId).containsKey(chunkNo)) chunksDataReceived.get(fileId).put(chunkNo, null);
    }

    public synchronized boolean isWaitingForChunk(String fileId, int chunkNo) {
        if (chunksDataReceived.containsKey(fileId) && 
            chunksDataReceived.get(fileId).containsKey(chunkNo)  &&
            chunksDataReceived.get(fileId).get(chunkNo) == null /* if null it hasn't been received yet */) return true;
        return false;
    }

    public synchronized void addChunkReceived(String fileId, int chunkNo, byte[] data) {
        if (isWaitingForChunk(fileId, chunkNo)) chunksDataReceived.get(fileId).put(chunkNo, data);

        if (!chunksReceived.containsKey(fileId)) chunksReceived.put(fileId, new ArrayList<Integer>());
        if (!chunksReceived.get(fileId).contains(chunkNo)) chunksReceived.get(fileId).add(chunkNo);
    }

    public synchronized void addChunkReceived(String fileId, int chunkNo) {
        if (!chunksReceived.containsKey(fileId)) chunksReceived.put(fileId, new ArrayList<Integer>());
        if (!chunksReceived.get(fileId).contains(chunkNo)) chunksReceived.get(fileId).add(chunkNo);
    }

    public synchronized boolean hasReceivedChunk(String fileId, int chunkNo) {
        return chunksReceived.containsKey(fileId) && chunksReceived.get(fileId).contains(chunkNo);
    }

    public synchronized boolean hasReceivedChunkData(String fileId, int chunkNo) {
        if (chunksDataReceived.containsKey(fileId) && 
            chunksDataReceived.get(fileId).containsKey(chunkNo)  &&
            chunksDataReceived.get(fileId).get(chunkNo) != null /* if null it hasn't been received yet */) return true;
        return false;
    }

    public synchronized boolean hasReceivedAllChunksData(String fileId) {
        if (!chunksDataReceived.containsKey(fileId)) {
            Logger.error("I have no entry for file with id '" + fileId + "' in the chunk reception map (not waiting for it)");
            return true;
        }
        return !chunksDataReceived.get(fileId).values().contains(null);  // has received all chunks if no chunk entry has the null value
    }

    public synchronized byte[] getReceivedChunkData(String fileId, int chunkNo) {
        if (!hasReceivedChunkData(fileId, chunkNo)) return null;
        return chunksDataReceived.get(fileId).get(chunkNo);
    }

    public synchronized List<byte[]> getFileChunks(String fileId) {
        Collection<Integer> keys = chunksDataReceived.get(fileId).keySet();
        
        List<Integer> keysSorted = keys.stream().collect(Collectors.toList());
        List<byte[]> res = new ArrayList<>();

        for (int key : keysSorted) res.add(chunksDataReceived.get(fileId).get(key));

        return res;
    }
}
