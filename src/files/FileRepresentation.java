package files;

import chord.Chord;

import java.io.File;

public class FileRepresentation {
    private final int fileKey;
    private final byte[] data;

    public FileRepresentation(File file, byte[] data) throws Exception {
        this.fileKey = Chord.generateFileId(file);
        this.data = data;
    }

    public FileRepresentation(int fileKey, byte[] data) throws Exception {
        this.fileKey = fileKey;
        this.data = data;
    }

    public FileRepresentation(String path) throws Exception {
        this(new File(path), new FileManager().read(path));
    }

    public byte[] getData() {
        return data;
    }

    public int getFileKey() {
        return fileKey;
    }
}
