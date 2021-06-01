package files;

import java.io.FileInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import exceptions.ArgsException;
import exceptions.ArgsException.Type;
import state.PeerState;

public class FileManager {
    private final String rootDir;
    public static AsynchronousFileChannel peerStateChannel = null;

    public FileManager(String rootDir) {
        this.rootDir = rootDir;
        createDir(rootDir);
    }
    
    public FileManager() {
        this.rootDir = "../filesystem";
        createDir(rootDir);
    }

    public static void createPeerStateAsynchronousChannel(String rootDir) throws IOException {
        createDir(rootDir);
        FileManager.peerStateChannel = AsynchronousFileChannel.open(Paths.get(rootDir + "/" + PeerState.stateFileName), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    }

    public static void createDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) dir.mkdirs();
    }

    public void writeFile(String fileName, List<byte[]> chunks) throws IOException {
        
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(Paths.get(this.rootDir + "/" + fileName), StandardOpenOption.WRITE, StandardOpenOption.CREATE);

        long offset = 0;
        for (byte[] chunk : chunks) {
            ByteBuffer buffer = ByteBuffer.wrap(chunk);
            channel.write(buffer, offset);
            offset += chunk.length;
        }

        channel.force(true);

    }

    public void write(String file, byte[] data) throws IOException {
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(Paths.get(this.rootDir + "/" + file), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        
        channel.write(buffer, 0);

        channel.force(true);

    }

    public static void write(AsynchronousFileChannel channel, byte[] data) throws IOException {
        channel.write(ByteBuffer.wrap(data), 0);
        channel.force(true);
    }

    public void writeBackedupFile(int fileId, byte[] data) throws IOException {
        write("f" + fileId, data);
    }

    public byte[] read(String file) throws IOException, ArgsException {
        String path = this.rootDir + "/" + file;
        
        File f = new File(path);
        if (!f.exists()) throw new ArgsException(Type.FILE_DOESNT_EXIST, path);

        FileInputStream in = new FileInputStream(path);
        byte[] data = in.readAllBytes();
        in.close();
        return data;
    }

    public byte[] readBackedUpFile(int fileKey) throws IOException, ArgsException {
        return read("f" + fileKey);
    }

    public void deleteBackedUpFile(int fileKey) {
        new File(this.rootDir + "/f" + fileKey).delete();
    }
}
