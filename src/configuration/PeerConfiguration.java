package configuration;

import chord.Chord;
import files.FileManager;
import server.DataBucket;
import server.ServerRouter;
import sslengine.SSLClient;
import sslengine.SSLServer;
import sslengine.ServerThread;
import state.PeerState;
import utils.IPFinder;
import utils.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;

public class PeerConfiguration {
    private final String serviceAccessPoint;
    private final PeerState state;
    private final ScheduledThreadPoolExecutor threadScheduler;
    private final SSLServer server;
    private final Chord chord;
    private final DataBucket dataBucket;

    public PeerConfiguration(String serviceAccessPoint, int serverPort) throws Exception {
        this(serviceAccessPoint, serverPort, null);
    }

    public PeerConfiguration(String serviceAccessPoint, int serverPort, InetSocketAddress preexistingNode) throws Exception {
        this.serviceAccessPoint = serviceAccessPoint;
        
        this.dataBucket = new DataBucket();
        this.threadScheduler = new ScheduledThreadPoolExecutor(30);

        String ip = IPFinder.find().getHostAddress();
        this.server = new SSLServer(ip, serverPort, new ServerRouter(this));
        this.threadScheduler.submit(new ServerThread(this.server));

        if (preexistingNode != null)
            this.chord = new Chord(this, new InetSocketAddress(ip, serverPort), preexistingNode);
        else 
            this.chord = new Chord(this, new InetSocketAddress(ip, serverPort));

        SSLClient.queue.setConfiguration(this);

        this.state = PeerState.read(getRootDir());
        FileManager.createPeerStateAsynchronousChannel(getRootDir());

        Logger.log("My IP is " + ip + "\nMy Chord ring id is " + this.chord.getId());
    }

    public DataBucket getDataBucket() {
        return dataBucket;
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

    public int getRandomDelay(int bound, int offset) {
        return ThreadLocalRandom.current().nextInt(bound) + offset;
    }

    public PeerState getPeerState() {
        return state;
    }

    public String getRootDir() {
        return "../filesystem/p" + getPeerId();
    }

    public int getPeerId() {
        return chord.getId();
    }

    public String getServiceAccessPoint() {
        return serviceAccessPoint;
    }
}
