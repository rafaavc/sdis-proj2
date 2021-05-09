package configuration;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;

import channels.MulticastChannel;
import channels.handlers.strategies.BackupStrategy;
import channels.handlers.strategies.EnhancedBackupStrategy;
import channels.handlers.strategies.EnhancedRestoreStrategy;
import channels.handlers.strategies.RestoreStrategy;
import channels.handlers.strategies.VanillaBackupStrategy;
import channels.handlers.strategies.VanillaRestoreStrategy;
import exceptions.ArgsException;
import files.FileManager;
import messages.trackers.ChunkTracker;
import state.PeerState;

public class PeerConfiguration {
    private final int peerId;
    private final String serviceAccessPoint;
    private final ProtocolVersion protocolVersion;
    private final MulticastChannel mc, mdb, mdr;
    private final PeerState state;
    private final ChunkTracker chunkTracker;
    private final ScheduledThreadPoolExecutor threadScheduler;

    public PeerConfiguration(ProtocolVersion protocolVersion, int peerId, String serviceAccessPoint, MulticastChannel mc, MulticastChannel mdb, MulticastChannel mdr) throws Exception {
        this.protocolVersion = protocolVersion;
        this.peerId = peerId;
        this.serviceAccessPoint = serviceAccessPoint;
        this.mc = mc;
        this.mdb = mdb;
        this.mdr = mdr;

        this.state = PeerState.read(getRootDir());
        FileManager.createPeerStateAsynchronousChannel(getRootDir());

        this.chunkTracker = new ChunkTracker();
        this.threadScheduler = new ScheduledThreadPoolExecutor(30);
    }

    public ScheduledThreadPoolExecutor getThreadScheduler() {
        return threadScheduler;
    }

    public int getRandomDelay(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    public int getRandomDelay(int bound, int offset) {
        return ThreadLocalRandom.current().nextInt(bound) + offset;
    }

    public PeerState getPeerState() {
        return state;
    }

    public String getRootDir() {
        return "../filesystem/" + this.peerId;
    }

    public ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public BackupStrategy getBackupStrategy() throws ArgsException {
        if (this.protocolVersion.equals("1.0")) return new VanillaBackupStrategy(this);
        if (this.protocolVersion.equals("1.1")) return new EnhancedBackupStrategy(this);
        return null;
    }

    public RestoreStrategy getRestoreStrategy() throws ArgsException {
        if (this.protocolVersion.equals("1.0")) return new VanillaRestoreStrategy(this);
        if (this.protocolVersion.equals("1.1")) return new EnhancedRestoreStrategy(this);
        return null;
    }

    public int getPeerId() {
        return peerId;
    }

    public String getServiceAccessPoint() {
        return serviceAccessPoint;
    }

    public MulticastChannel[] getChannels() {
        return new MulticastChannel[] { this.mc, this.mdb, this.mdr };
    }

    public MulticastChannel getMC() {
        return mc;
    }
    
    public MulticastChannel getMDB() {
        return mdb;
    }

    public MulticastChannel getMDR() {
        return mdr;
    }

    public ChunkTracker getChunkTracker() {
        return chunkTracker;
    }
}
