import actions.*;
import configuration.ClientInterface;
import configuration.PeerConfiguration;
import state.PeerState;
import utils.Logger;
import utils.Result;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CompletableFuture;

public class Peer extends UnicastRemoteObject implements ClientInterface {
    private static final long serialVersionUID = 5157944159616018684L;
    private final PeerConfiguration configuration;

    public Peer(PeerConfiguration configuration) throws Exception {
        this.configuration = configuration;

        Logger.log(this.getPeerState() + "\nReady!");

        new CheckFiles(configuration).execute();
    }

    public void writeState() throws IOException {
        this.getPeerState().write();
    }

    /* RMI interface */

    public Result backup(String filePath, int desiredReplicationDegree) throws RemoteException {

        CompletableFuture<Result> f = new CompletableFuture<>();
        
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

