package files;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import exceptions.ChunkSizeExceeded;
import exceptions.InvalidChunkNo;

public class Chunk {
    final static Integer MAX_CHUNK_BYTES = 64000;  // 64 KBytes
    private String fileId;
    private Integer chunkNo;
    private byte[] data;

    public Chunk(String fileId, Integer chunkNo, byte[] data) throws ChunkSizeExceeded, InvalidChunkNo {
        if (data.length > MAX_CHUNK_BYTES) throw new ChunkSizeExceeded(String.valueOf(data.length));
        if (chunkNo > 999999 || chunkNo < 0) throw new InvalidChunkNo("Exceeds 999999 (" + chunkNo + ")"); // maximum 6 chars

        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.data = data;
    }

    public String getFileId() {
        return fileId;
    }

    public Integer getChunkNo() {
        return chunkNo;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Chunk(" + fileId + ":" + chunkNo + ", size: " + data.length + " bytes)";
    }

    public static List<Chunk> getChunks(String fileId, byte[] data) throws ChunkSizeExceeded, InvalidChunkNo {
        List<Chunk> chunkList = new ArrayList<>();

        int offset = 0;
        while(true) {
            int upperLimit = data.length < (offset+1)*Chunk.MAX_CHUNK_BYTES ? data.length : (offset+1)*Chunk.MAX_CHUNK_BYTES;
            byte[] chunkData = Arrays.copyOfRange(data, offset*Chunk.MAX_CHUNK_BYTES, upperLimit);
            chunkList.add(new Chunk(fileId, offset, chunkData));

            if (upperLimit >= data.length) return chunkList;
            offset++;
        }
    }
}
