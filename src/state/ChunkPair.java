package state;

import java.io.Serializable;

public class ChunkPair implements Serializable {
    private static final long serialVersionUID = 4176934406107100902L;

    protected final int chunkNo;
    protected int perceivedReplicationDegree;

    public ChunkPair(int chunkNo, int perceivedReplicationDegree) {
        this.chunkNo = chunkNo;
        this.perceivedReplicationDegree = perceivedReplicationDegree;
    }

    public int getChunkNo() {
        return chunkNo;
    }

    public int getPerceivedReplicationDegree() {
        return perceivedReplicationDegree;
    }

    public void setPerceivedReplicationDegree(int perceivedReplicationDegree) {
        this.perceivedReplicationDegree = perceivedReplicationDegree;
    }
}
