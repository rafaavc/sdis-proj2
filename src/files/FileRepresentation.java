package files;

import java.io.File;

import chord.Chord;

public class FileRepresentation {
    private final int fileKey;
    private final byte[] data;

    public FileRepresentation(File file, byte[] data) throws Exception {
        this.fileKey = Chord.generateFileId(file);
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