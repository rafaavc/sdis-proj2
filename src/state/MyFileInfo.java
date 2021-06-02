package state;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MyFileInfo implements Serializable {
    private static final long serialVersionUID = 8712295865807115205L;
    
    private final String fileName;
    private final int fileKey, desiredReplicationDegree, perceivedReplicationDegree;
    private final long byteAmount;

    public MyFileInfo(String pathName, long fileSize, int fileKey, int desiredReplicationDegree, int perceivedReplicationDegree) {
        Path path = Paths.get(pathName);
        this.fileName = path.getFileName().toString();
        this.fileKey = fileKey;
        this.desiredReplicationDegree = desiredReplicationDegree;
        this.perceivedReplicationDegree = perceivedReplicationDegree;
        this.byteAmount = fileSize;
    }

    public long getByteAmount() {
        return byteAmount;
    }

    public int getFileKey() {
        return fileKey;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public String toString() {
        return fileName + ": " + fileKey + " (desired=" + desiredReplicationDegree + ", perceived=" + perceivedReplicationDegree + ")";
    }
}
