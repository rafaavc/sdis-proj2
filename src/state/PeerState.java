package state;

import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import files.FileManager;
import utils.Logger;



public class PeerState implements Serializable {
    private static final long serialVersionUID = 3474820596488159542L;

    public static String stateFileName = "metadata";
    private final String dir;

    private final ConcurrentMap<String, FileInfo> files = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<Integer, ChunkInfo>> chunks = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, String> fileNameId = new ConcurrentHashMap<>();

    private final List<String> deletedFiles = new ArrayList<>();

    private int maximumSpaceAvailable = -1;

    public PeerState(String dir) {
        this.dir = dir;
    }

    public synchronized void setMaximumStorageAvailable(int maximumSpaceAvailable) {
        this.maximumSpaceAvailable = maximumSpaceAvailable;
        writeState();
    }

    public int getMaximumStorage() {
        return this.maximumSpaceAvailable;
    }

    public float getStorageAvailable() {
        return getMaximumStorage() - getOccupiedStorage();
    }

    public float getOccupiedStorage() {
        float occupiedStorage = 0;
        for (ChunkInfo chunk : getChunks()) occupiedStorage += chunk.getSize();
        return occupiedStorage;
    }

    public String getFileId(String fileName) {
        return fileNameId.get(fileName);
    }

    public void addFile(FileInfo f) {
        synchronized (files) {
            files.put(f.getFileId(), f);
            fileNameId.put(f.getFileName(), f.getFileId());
            writeState();
        }
    }

    public void addDeletedFile(String fileId) {
        synchronized (deletedFiles) {
            if (!deletedFiles.contains(fileId)) deletedFiles.add(fileId);
            writeState();
        }
    }

    public void removeDeletedFile(String fileId) {
        synchronized (deletedFiles) {
            deletedFiles.remove(fileId);
            writeState();
        }
    }

    public boolean isDeleted(String fileId) {
        synchronized (deletedFiles) {
            return deletedFiles.contains(fileId);
        }
    }

    public void deleteFile(String fileId) {
        synchronized (files) {
            fileNameId.remove(files.get(fileId).getFileName());
            files.remove(fileId);
            writeState();
        }
    }

    public boolean ownsFileWithId(String fileId) {
        return files.containsKey(fileId);
    }

    public boolean ownsFileWithName(String fileName) {
        return fileNameId.containsKey(fileName);
    }

    public void addChunk(ChunkInfo c) {
        synchronized (chunks) {
            if (chunks.containsKey(c.getFileId()) && !chunks.get(c.getFileId()).containsKey(c.getChunkNo())) {
                chunks.get(c.getFileId()).put(c.getChunkNo(), c);
            } else if (!chunks.containsKey(c.getFileId())) {
                ConcurrentMap<Integer, ChunkInfo> info = new ConcurrentHashMap<>();
                info.put(c.getChunkNo(), c);
                chunks.put(c.getFileId(), info);
            }
            writeState();
        }
        try {
            this.write();
        }
        catch(IOException e)
        {
            Logger.error("Error writing peer state. " + e.getMessage());
        }
    }

    public void updateChunkPerceivedRepDegree(String fileId, int chunkNo, int perceivedReplicationDegree) {
        synchronized (chunks) {
            if (chunks.containsKey(fileId)) {
                Map<Integer, ChunkInfo> fileChunks = chunks.get(fileId);
                if (fileChunks.containsKey(chunkNo)) {
                    fileChunks.get(chunkNo).setPerceivedReplicationDegree(perceivedReplicationDegree); // TODO this may need improvements
                }
            }
            writeState();
        }
    }

    public void deleteChunk(ChunkInfo c) {
        synchronized (chunks) {
            chunks.get(c.getFileId()).remove(c.getChunkNo());
            writeState();
        }
    }

    public void deleteFileChunks(String fileId) {
        synchronized (chunks) {
            this.chunks.remove(fileId);
            writeState();
        }
    }

    public Set<String> getBackedUpFileIds() {
        return this.chunks.keySet();
    }

    public FileInfo getFile(String fileId) {
        return files.get(fileId);
    }

    public boolean hasFileChunks(String fileId) {
        return chunks.containsKey(fileId);
    }

    public boolean hasChunk(String fileId, int chunkNo) {
        return chunks.containsKey(fileId) && chunks.get(fileId).containsKey(chunkNo);
    }

    public ChunkInfo getChunk(String fileId, int chunkNo) {
        return chunks.containsKey(fileId) ? chunks.get(fileId).get(chunkNo) : null;
    }

    public List<ChunkInfo> getChunks() {
        List<ChunkInfo> res = new ArrayList<>();

        for (Map<Integer, ChunkInfo> m : this.chunks.values()) {
            for (ChunkInfo c : m.values()) res.add(c);
        }

        return res;
    }

    public static PeerState read(String dir) throws IOException, ClassNotFoundException {
        File f = new File(dir + "/" + stateFileName);
        if (!f.exists()) {
            Logger.log("Didn't find a stored state, creating new one.");
            return new PeerState(dir);
        }

        FileInputStream fin = new FileInputStream(dir + "/" + stateFileName);
        ObjectInputStream in = new ObjectInputStream(fin);
        
        PeerState state = (PeerState) in.readObject();

        in.close();

        return state;
    }

    public void write() throws IOException {
        File f = new File(this.dir);
        if (!f.exists()) f.mkdirs();

        ByteArrayOutputStream bis = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bis);
        synchronized (deletedFiles) {
            out.writeObject(this);
        }
        out.flush();
        out.close();

        byte[] data = bis.toByteArray();
        FileManager.write(FileManager.peerStateChannel, data);
    }

    private void writeState() {
        try {
            this.write();
        }
        catch(IOException e)
        {
            Logger.error("Error writing peer state. " + e.getMessage());
        }
    }

    @Override
    public String toString() {

        StringBuilder res = new StringBuilder();

        if (chunks.isEmpty() && files.isEmpty()) res.append("I haven't sent any files nor backed up any chunks.\n");

        if (!chunks.isEmpty()) {
            res.append("I've stored these chunks:\n");
            for (Map<Integer, ChunkInfo> chunks : chunks.values()) {
                for (ChunkInfo chunk : chunks.values()) {
                    res.append("- ");
                    res.append(chunk);
                    res.append("\n");
                }
            }
            res.append("\n");
        }

        if (!files.isEmpty()) {
            res.append("I've sent these files for backup:\n");
            for (FileInfo file : files.values()) {
                res.append("- ");
                res.append(file);
                res.append("\n");
            }
            res.append("\n");
        }
        res.append("Maximum storage: " + getMaximumStorage() + "\n");
        res.append("Occupied storage: " + getOccupiedStorage() + "\n");

        return res.toString();
    }
}
