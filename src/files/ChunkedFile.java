package files;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import exceptions.ArgsException;
import exceptions.ChunkSizeExceeded;
import exceptions.InvalidChunkNo;

public class ChunkedFile {
    private final String fileId;
    private final List<Chunk> chunks;

    public static String generateFileId(File file) throws IOException, NoSuchAlgorithmException {
        BasicFileAttributes attr = Files.readAttributes(Path.of("../filesystem/" + file.getPath()), BasicFileAttributes.class);

        String original = file.getPath() + attr.lastModifiedTime() + attr.creationTime() + attr.size();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(original.getBytes());

        StringBuilder idBuilder = new StringBuilder();
        for (byte b : hash) {
            idBuilder.append(String.format("%02x", b));
        }

        return idBuilder.toString();
    }

    public ChunkedFile(File file, byte[] data) throws IOException, NoSuchAlgorithmException, ChunkSizeExceeded, InvalidChunkNo {
        this.fileId = ChunkedFile.generateFileId(file);
        this.chunks = Chunk.getChunks(fileId, data);
    }

    public ChunkedFile(String path) throws IOException, ChunkSizeExceeded, InvalidChunkNo, ArgsException, NoSuchAlgorithmException {
        this(new File(path), new FileManager().read(path));
    }

    public List<Chunk> getChunks() {
        return chunks;
    }

    public String getFileId() {
        return fileId;
    }    
}
