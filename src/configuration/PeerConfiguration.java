package configuration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;

import chord.Chord;
import exceptions.ArgsException;
import files.FileManager;
import messages.trackers.ChunkTracker;
import server.Router;
import server.ServerRouter;
import server.handlers.strategies.BackupStrategy;
import server.handlers.strategies.EnhancedBackupStrategy;
import server.handlers.strategies.EnhancedRestoreStrategy;
import server.handlers.strategies.RestoreStrategy;
import server.handlers.strategies.VanillaBackupStrategy;
import server.handlers.strategies.VanillaRestoreStrategy;
import sslengine.SSLServer;
import sslengine.ServerThread;
import state.PeerState;
import utils.IPFinder;
import utils.Logger;

public class PeerConfiguration {
    private final String serviceAccessPoint;
    private final ProtocolVersion protocolVersion;
    private final PeerState state;
    private final ChunkTracker chunkTracker;
    private final ScheduledThreadPoolExecutor threadScheduler;
    private final SSLServer server;
    private final Chord chord;

    public PeerConfiguration(ProtocolVersion protocolVersion, String serviceAccessPoint, int serverPort) throws Exception {
        this(protocolVersion, serviceAccessPoint, serverPort, null);
    }

    public PeerConfiguration(ProtocolVersion protocolVersion, String serviceAccessPoint, int serverPort, InetSocketAddress preexistingNode) throws Exception {
        this.protocolVersion = protocolVersion;
        this.serviceAccessPoint = serviceAccessPoint;

        this.chunkTracker = new ChunkTracker();
        this.threadScheduler = new ScheduledThreadPoolExecutor(30);

        String ip = IPFinder.find().getHostAddress();
        this.server = new SSLServer(ip, serverPort, new ServerRouter(this));
        this.threadScheduler.submit(new ServerThread(this.server));

        if (preexistingNode != null)
            this.chord = new Chord(this, new InetSocketAddress(ip, serverPort), preexistingNode);
        else 
            this.chord = new Chord(this, new InetSocketAddress(ip, serverPort));

        this.state = PeerState.read(getRootDir());
        FileManager.createPeerStateAsynchronousChannel(getRootDir());

        Logger.log("My IP is " + ip + "\nMy Chord ring id is " + this.chord.getId());
    }

    public Chord getChord() {
        return chord;
    }

    public SSLServer getServer() {
        return server;
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
        return "../filesystem/p" + getPeerId();
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
        return chord.getId();
    }

    public String getServiceAccessPoint() {
        return serviceAccessPoint;
    }

    public ChunkTracker getChunkTracker() {
        return chunkTracker;
    }
}
