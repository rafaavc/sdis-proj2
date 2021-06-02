import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

import actions.Restore;
import configuration.ClientInterface;
import configuration.PeerConfiguration;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import state.PeerState;
import actions.Backup;
import actions.Delete;
import actions.Reclaim;
import utils.Logger;
import utils.Result;
import utils.ResultWithData;

public class Peer extends UnicastRemoteObject implements ClientInterface {
    private static final long serialVersionUID = 5157944159616018684L;
    private final PeerConfiguration configuration;

    public Peer(PeerConfiguration configuration) throws Exception {
        this.configuration = configuration;

        Logger.log(this.getPeerState());

        Logger.log("Ready!");

//        new CheckDeleted(configuration).execute();
    }

    public void writeState() throws IOException {
        this.getPeerState().write();
    }

    /* RMI interface */

    public void sendMessageToServer(int n) throws RemoteException {
        try {
             ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(50);

             Consumer<Integer> send = (Integer i) -> {
                 try {
                    Future<Message> f = SSLClient.sendQueued(configuration, configuration.getChord().getSuccessor().getInetSocketAddress(), MessageFactory.getLookupMessage(11, 574), true);
                    f.get();
                 } catch(Exception e) {
                    Logger.error(e, true);
                 }
             };

             for (int i = 0; i < n; i++) {
                 executor.execute(() -> send.accept(1));

                 // Thread.sleep(50 + (int) (Math.random() * 50));
             }
            
        } catch (Exception e) {
            Logger.error(e, true);
        }
    }

    public Result backup(String filePath, int desiredReplicationDegree) throws RemoteException {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();

        if (getPeerState().ownsFileWithName(fileName)) 
        {
            int fileKey = getPeerState().getFileKey(fileName);
            Logger.log("The file " + fileName + " had an older version. Deleting it.");

            new Delete(new CompletableFuture<>(), configuration, fileKey).execute();
        }

        CompletableFuture<ResultWithData<Integer>> f = new CompletableFuture<>();
        
        try
        {
            new Backup(f, configuration, filePath, desiredReplicationDegree).execute();
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
        new Restore(f, configuration, getPeerState().getFileKey(fileName)).execute();

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
        int fileId = getPeerState().getFileKey(fileName);
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

        try
        {
            new Reclaim(f, configuration, kb).execute();
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

