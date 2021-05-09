import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CompletableFuture;

import channels.ChannelListener;
import channels.MulticastChannel;
import channels.handlers.Handler;
import configuration.ClientInterface;
import configuration.PeerConfiguration;
import state.PeerState;
import actions.Backup;
import actions.CheckDeleted;
import actions.Delete;
import actions.Restore;
import utils.Logger;
import utils.Result;
import actions.Reclaim;

public class Peer extends UnicastRemoteObject implements ClientInterface {
    private static final long serialVersionUID = 5157944159616018684L;
    private final PeerConfiguration configuration;

    public Peer(PeerConfiguration configuration) throws Exception {
        this.configuration = configuration;

        Logger.log(this.getPeerState());

        for (MulticastChannel channel : this.configuration.getChannels())
        {
            new ChannelListener(channel, Handler.get(this.configuration, channel.getType()), configuration.getThreadScheduler()).start();
        }

        Logger.log("Running on protocol version " + configuration.getProtocolVersion() + ". Ready!");

        if (configuration.getProtocolVersion().equals("1.1")) {
            new CheckDeleted(configuration).execute();
        }
    }

    public void writeState() throws IOException {
        this.getPeerState().write();
    }

    /* RMI interface */

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

        new Backup(f, configuration, filePath, desiredReplicationDegree).execute();

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

    public PeerState getPeerState() {
        return this.configuration.getPeerState();
    }

    public void hi() throws RemoteException {
        Logger.log("Hi");
    }
}

