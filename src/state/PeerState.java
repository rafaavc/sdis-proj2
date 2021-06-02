package state;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import files.FileManager;
import utils.Logger;



public class PeerState implements Serializable {
    private static final long serialVersionUID = 3474820596488159542L;

    public static String stateFileName = "metadata";
    private final String dir;

    private final ConcurrentMap<Integer, MyFileInfo> myFiles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> myFileNameKey = new ConcurrentHashMap<>();

    private final ConcurrentMap<Integer, OthersFileInfo> othersFiles = new ConcurrentHashMap<>();


    /**
     *  List with the deleted files correspondent to this node
     *  If a delete request reaches this node, and it does not have the file not a pointer to its location,
     *  when the node that has the file, which is before this one in the ring, rejoins,
     *  he can ask this one whether a file that could be his was deleted
     */
    private final List<Integer> deletedFiles = new ArrayList<>();

    /**
     *  List with all the files that should be here but are in the successor
     *  (and the successor either really has them or also has a pointer to his successor)
     *
     *  This happens when the file is backed up but then a peer that is a closer successor of the key of the file
     *  joins the ring. If this pointer list didn't exist, a future lookup of the file's key would give this node
     *  and he wouldn't know what to do.
     *
     *  Eventually, each of these points could be transferred from the successor to this node
     *  (for example, with a delay of 10 seconds poll this list and recover one of the files, until it is empty)
     */
    private final List<Integer> filePointers = new ArrayList<>();

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
        for (OthersFileInfo chunk : getOthersFiles()) occupiedStorage += chunk.getSize();
        return occupiedStorage;
    }

    public int getFileKey(String fileName) {
        return myFileNameKey.get(fileName);
    }

    public void addFile(MyFileInfo f) {
        synchronized (myFiles) {
            myFiles.put(f.getFileKey(), f);
            myFileNameKey.put(f.getFileName(), f.getFileKey());
            writeState();
        }
    }

    public void addDeletedFile(Integer fileKey) {
        synchronized (deletedFiles) {
            if (!deletedFiles.contains(fileKey)) deletedFiles.add(fileKey);
            writeState();
        }
    }

    public void removeDeletedFile(Integer fileKey) {
        synchronized (deletedFiles) {
            deletedFiles.remove(fileKey);
            writeState();
        }
    }

    public boolean isDeleted(Integer fileKey) {
        synchronized (deletedFiles) {
            return deletedFiles.contains(fileKey);
        }
    }

    public void addPointerFile(Integer fileKey) {
        synchronized (filePointers) {
            if (!filePointers.contains(fileKey)) filePointers.add(fileKey);
            writeState();
        }
    }

    public void removePointerFile(Integer fileKey) {
        synchronized (filePointers) {
            filePointers.remove(fileKey);
            writeState();
        }
    }

    public boolean isPointerFile(Integer fileKey) {
        synchronized (filePointers) {
            return filePointers.contains(fileKey);
        }
    }

    public List<Integer> getFilePointers() {
        return filePointers;
    }

    public void deleteFile(Integer fileKey) {
        synchronized (myFiles) {
            myFileNameKey.remove(myFiles.get(fileKey).getFileName());
            myFiles.remove(fileKey);
            writeState();
        }
    }

    public List<Integer> getDeletedFiles() {
        return deletedFiles;
    }

    public void addBackedUpFile(OthersFileInfo file) {
        synchronized (othersFiles) {
            othersFiles.put(file.getFileKey(), file);
            writeState();
        }
    }

    public boolean ownsFileWithKey(Integer fileKey) {
        return myFiles.containsKey(fileKey);
    }

    public boolean ownsFileWithName(String fileName) {
        return myFileNameKey.containsKey(fileName);
    }

    public MyFileInfo getFile(Integer fileId) {
        return myFiles.get(fileId);
    }


    public Set<Integer> getBackedUpFileIds() {
        return othersFiles.keySet();
    }

    public boolean hasBackedUpFile(Integer fileId) {
        return othersFiles.containsKey(fileId);
    }

    public OthersFileInfo getBackedUpFile(Integer fileId) {
        return othersFiles.getOrDefault(fileId, null);
    }

    public List<OthersFileInfo> getOthersFiles() {
        return new ArrayList<>(othersFiles.values());
    }


    public void deleteOthersFile(Integer fileKey) {
        synchronized (othersFiles) {
            othersFiles.remove(fileKey);
            writeState();
        }
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

        if (othersFiles.isEmpty() && myFiles.isEmpty()) res.append("I haven't sent nor backed up any files.\n");

        if (!othersFiles.isEmpty()) {
            res.append("I've stored these files:\n");
            for (OthersFileInfo f : othersFiles.values()) {
                res.append("- ").append(f).append("\n");
            }
            res.append("\n");
        }

        if (!myFiles.isEmpty()) {
            res.append("I've sent these files for backup:\n");
            for (MyFileInfo file : myFiles.values()) {
                res.append("- ").append(file).append("\n");
            }
            res.append("\n");
        }

        if (!filePointers.isEmpty()) {
            res.append("I've got these file pointers:\n");
            for (int fileKey : filePointers) {
                res.append("- ").append(fileKey).append("\n");
            }
            res.append("\n");
        }

        if (!deletedFiles.isEmpty()) {
            res.append("I've got these deleted files:\n");
            for (int fileKey : deletedFiles) {
                res.append("- ").append(fileKey).append("\n");
            }
            res.append("\n");
        }

        res.append("Maximum storage: ").append(getMaximumStorage()).append("\n")
                .append("Occupied storage: ").append(getOccupiedStorage()).append("\n");

        return res.toString();
    }
}
