package state;

public class ChunkInfo extends ChunkPair {
    private static final long serialVersionUID = -7039536338647806374L;

    private final String fileId;
    private final int desiredReplicationDegree;
    private final float size; // KB
    
    public ChunkInfo(String fileId, float size, int chunkNo, int perceivedReplicationDegree, int desiredReplicationDegree) {
        super(chunkNo, perceivedReplicationDegree);
        this.fileId = fileId;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.size = size;
    }

    public float getSize() {
        return this.size;
    }

    public String getFileId() {
        return fileId;
    }

    public int getDesiredReplicationDegree() {
        return desiredReplicationDegree;
    }

    @Override
    public String toString() {
        return fileId + ":" + chunkNo + " | replication desired: " + desiredReplicationDegree + ", perceived: " + perceivedReplicationDegree;
    }

    @Override
    public boolean equals(Object c) { 
        if (this == c) return true;
        if (c == null) return false;

        if (c.getClass() != this.getClass()) return false;

        return this.fileId.equals(((ChunkInfo)c).getFileId()) && this.chunkNo == ((ChunkInfo)c).getChunkNo();
    }
}
