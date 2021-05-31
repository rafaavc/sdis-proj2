import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

import javax.net.ssl.SSLEngineResult;

import configuration.ClientInterface;
import configuration.PeerConfiguration;
import files.FileManager;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import state.PeerState;
import actions.Backup;
import actions.CheckDeleted;
import actions.Delete;
import actions.Restore;
import chord.ChordNode;
import utils.Logger;
import utils.Result;
import actions.Reclaim;

public class Peer extends UnicastRemoteObject implements ClientInterface {
    private static final long serialVersionUID = 5157944159616018684L;
    private final PeerConfiguration configuration;

    public Peer(PeerConfiguration configuration) throws Exception {
        this.configuration = configuration;

        Logger.log(this.getPeerState());

        Logger.log("Running on protocol version " + configuration.getProtocolVersion() + ". Ready!");

        if (configuration.getProtocolVersion().equals("1.1")) {
            new CheckDeleted(configuration).execute();
        }
    }

    public void writeState() throws IOException {
        this.getPeerState().write();
    }

    /* RMI interface */

    public void sendMessageToServer(int n) throws RemoteException {
        try {
            // configuration.getChord().lookup(configuration.getChord().getSuccessor().getInetSocketAddress(), 10);

            // ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(50);

            // SSLClient client = new SSLClient(configuration.getChord().getSuccessor().getInetAddress().getHostAddress(), configuration.getChord().getSuccessor().getPort());
            // client.connect();

            // Consumer<Integer> send = (Integer i) -> {
            //     try {
            //         //client.write(new FileManager().read("../../lorem.txt"));
            //         Message message = client.sendAndReadReply(new MessageFactory(configuration.getProtocolVersion()).getLookupMessage(configuration.getChord().getId(), i));
            //         Logger.log(message.toString());

            //     } catch(Exception e) {
            //         Logger.error(e, true);
            //     }
            // };

            // for (int i = 0; i < n; i++) {
            //     int j = i;
            //     executor.execute(() -> send.accept(j+1));
                
            //     // Thread.sleep(50 + (int) (Math.random() * 50));
            // }
            
        } catch (Exception e) {
            Logger.error(e, true);
        }
    }

    public Result backup(String filePath, int desiredReplicationDegree) throws RemoteException {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();

        if (getPeerState().ownsFileWithName(fileName)) 
        {
            String fileId = getPeerState().getFileId(fileName);
            Logger.log("The file " + fileName + " had an older version. Deleting it.");

            new Delete(new CompletableFuture<>(), configuration, fileId).execute();
        }

        CompletableFuture<Result> f = new CompletableFuture<>();

        //new Backup(f, configuration, filePath, desiredReplicationDegree).execute();
        //Logger.todo(this);
        
        try
        {
            return f.get();
        }
        catch(Exception e)
        {
            Logger.error(e, true);
        }
        return null;
    }

    public Result restore(String fileName) throws RemoteException {
        if (!getPeerState().ownsFileWithName(fileName)) 
        {
            Logger.error("The file '" + fileName + "' doesn't exist in my history.");
            return new Result(false, "The file '" + fileName + "' doesn't exist in the peer's history.");
        }
        CompletableFuture<Result> f = new CompletableFuture<>();
        new Restore(f, configuration, getPeerState().getFileId(fileName)).execute();

        try
        {
            return f.get();
        }
        catch(Exception e)
        {
            Logger.error(e, true);
        }
        return null;
    }

    public Result delete(String fileName) throws RemoteException {
        if (!getPeerState().ownsFileWithName(fileName))
        {
            Logger.error("The file '" + fileName + "' doesn't exist in my history.");
            return new Result(false, "The file '" + fileName + "' doesn't exist in the peer's history.");
        }
        String fileId = getPeerState().getFileId(fileName);
        CompletableFuture<Result> f = new CompletableFuture<>();
        new Delete(f, configuration, fileId).execute();

        try
        {
            return f.get();
        }
        catch(Exception e)
        {
            Logger.error(e, true);
        }
        return null;
    }

    public Result reclaim(int kb) throws RemoteException {
        CompletableFuture<Result> f = new CompletableFuture<>();
        new Reclaim(f, configuration, kb).execute();

        try
        {
            return f.get();
        }
        catch(Exception e)
        {
            Logger.error(e, true);
        }
        return null;
    }

    public String getFingerTableString() {
        return this.configuration.getChord().toString();
    }

    public PeerState getPeerState() {
        return this.configuration.getPeerState();
    }

    public void hi() throws RemoteException {
        Logger.log("Hi");
    }
}

