package actions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import configuration.PeerConfiguration;
import configuration.ProtocolVersion;
import messages.MessageFactory;
import utils.Logger;
import utils.Result;

public class Delete {
    private final PeerConfiguration configuration;
    private final String fileId;
    private final CompletableFuture<Result> future;

    private class DeleteIter implements Runnable {
        private final byte[] msg;
        private int count;

        public DeleteIter(byte[] msg) {
            this.msg = msg;
            this.count = 1;
        }

        private DeleteIter(byte[] msg, int count) {
            this(msg);
            this.count = count;
        }

        @Override
        public void run() {
            try
            {
                configuration.getMC().send(msg);
            } 
            catch(Exception e) 
            {
                Logger.error(e, future);
            }
            if (count < 5) configuration.getThreadScheduler().schedule(new DeleteIter(msg, count + 1), 500, TimeUnit.MILLISECONDS);
            else future.complete(new Result(true, "Sent delete 5 times."));
        }
    }

    public Delete(CompletableFuture<Result> future, PeerConfiguration configuration, String fileId) {
        this.configuration = configuration;
        this.fileId = fileId;
        this.future = future;
    }

    public void execute() {
        configuration.getPeerState().addDeletedFile(fileId);
        this.configuration.getPeerState().deleteFile(fileId);
        
        try 
        {
            byte[] msg = new MessageFactory(new ProtocolVersion(1, 0)).getDeleteMessage(this.configuration.getPeerId(), fileId);
            configuration.getThreadScheduler().execute(new DeleteIter(msg));
        } 
        catch(Exception e) 
        {
            Logger.error(e, future);
        }
    }
}
