package state;

import java.io.Serializable;

public class OthersFileInfo implements Serializable {
    private static final long serialVersionUID = -7039536338647806374L;

    private final int fileKey;
    private final float size; // KB
    
    public OthersFileInfo(int fileKey, float size) {
        this.fileKey = fileKey;
        this.size = size;
    }

    public float getSize() {
        return this.size;
    }

    public int getFileKey() {
        return fileKey;
    }

    @Override
    public String toString() {
        return fileKey + " | size: " + size + "KB";
    }

    @Override
    public boolean equals(Object c) { 
        if (this == c) return true;
        if (c == null) return false;

        if (c.getClass() != this.getClass()) return false;

        return fileKey == ((OthersFileInfo) c).getFileKey();
    }
}
