package state;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MyFileInfo implements Serializable {
    private static final long serialVersionUID = 8712295865807115205L;
    
    private final String fileName;
    private final int fileKey, desiredReplicationDegree;

    public MyFileInfo(String pathName, int fileKey, int desiredReplicationDegree) {
        Path path = Paths.get(pathName);
        this.fileName = path.getFileName().toString();
        this.fileKey = fileKey;
        this.desiredReplicationDegree = desiredReplicationDegree;
    }

    public int getDesiredReplicationDegree() {
        return desiredReplicationDegree;
    }

    public int getFileKey() {
        return fileKey;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public String toString() {
        return fileName + ": " + fileKey + ", desired rep = " + desiredReplicationDegree + "\n";
    }
}
