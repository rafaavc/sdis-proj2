package chord;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import configuration.PeerConfiguration;
import exceptions.ArgsException;
import messages.MessageFactory;
import sslengine.SSLClient;

import java.lang.Math;

import utils.Logger;

public class Chord {
    private final List<ChordNode> fingerTable = new ArrayList<>();
    private final PeerConfiguration configuration;
    private final ChordNode self;
    private final int m;
    private final MessageFactory messageFactory;
    private ChordNode predecessor;

    /**
     * Instantiates the chord algorithm for this peer, making him join the P2P network
     * @param peerAddress the address of the peer that this class is in respect to.
     * @param port the port of the peer that this class is in respect to to.
     * @param preexistingNode the address of a peer that already belongs to the P2P network.
     * @throws Exception
     */
    public Chord(PeerConfiguration configuration, InetSocketAddress peerAddress, InetSocketAddress preexistingNode) throws Exception {
        int id = -1;        
        /*if (preexistingNode == null)*/ id = this.generateNodeId(peerAddress);
        //else id = this.getCollisionFreeId(peerAddress, preexistingNode);

        this.self = new ChordNode(peerAddress, id);
        this.m = 32; // an integer has 32 bits
        this.configuration = configuration;
        this.messageFactory = new MessageFactory(configuration.getProtocolVersion());

        /*if (preexistingNode == null)*/ this.create();
        //else this.join(preexistingNode);
    }

    public Chord(PeerConfiguration configuration, InetSocketAddress peerAddress) throws Exception {
        this(configuration, peerAddress, null);
    }

    private int generateNodeId(InetSocketAddress peerAddress) throws NoSuchAlgorithmException {
        String original = peerAddress.getAddress().getHostAddress() + peerAddress.getPort();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        return ByteBuffer.wrap(digest.digest(original.getBytes())).getInt();
    }

    private int getCollisionFreeId(InetSocketAddress peerAddress, InetSocketAddress preexistingPeerAddress) throws Exception {
        int id = generateNodeId(peerAddress);
        int originalId = id;

        while (true) {
            ChordNode respectiveNode = this.lookup(preexistingPeerAddress, id);  // Send lookup to the preexisting node
            
            if (respectiveNode.getId() == id)
            {
                id++;
                if (id == originalId)
                    throw new Exception("I can't enter chord ring because there is already one node in the ring.");
            } 
            else break;
        }

        return id;
    }

    public int getId() {
        return self.getId();
    }

    private void create() {
        this.predecessor = null;
        this.fingerTable.add(self);
    }

    private void join(InetSocketAddress preexistingNode) throws Exception {
        this.predecessor = null;

        // send LOOKUP message to the preexisting node
        // and set the successor to the value of the return
        ChordNode successor = this.lookup(self.getId());  // TODO change to the value returned by the LOOKUP message
        this.fingerTable.add(successor);
    }

    /**
     * Daniel
     * @throws Exception
     */
    public void updateFingers() throws Exception {
        for (int finger = 0; finger < this.m - 1; finger++) {
            ChordNode fingerValue = lookup(self.getId() + (int) Math.pow(2, finger));
            if (fingerTable.get(finger) != null) {
                fingerTable.set(finger, fingerValue);
            } else if (fingerTable.get(finger - 1) != null) { // if it's filled up to this point
                fingerTable.add(fingerValue);
            } else {
                Logger.error("updateFingers: finger table was not valid.");
            }
        }
    }

    /**
     * Daniel
     * Verifies if the predecessor of the node's successor is still the node itself
     */
    public void stabilize() throws Exception {
        ChordNode successor = fingerTable.get(0);
        
        SSLClient client = new SSLClient(successor.getInetSocketAddress().getHostName(), successor.getPort());
        client.write(messageFactory.getPredecessorMessage(self.getId()));
        client.read();
        
        int sucPredId = client.getPeerAppData().getInt();

        client.shutdown();

        if (sucPredId != -1) {
            ChordNode sucPredecessor = lookup(sucPredId);

            if (successor.getId() > self.getId()) {
                if (sucPredId > self.getId() && sucPredId < successor.getId()) {
                    fingerTable.set(0, sucPredecessor);
                }
            } else {
                if (sucPredId > self.getId() || sucPredId < successor.getId()) {
                    fingerTable.set(0, sucPredecessor);
                }
            }
        }

        notifyPredecessor(fingerTable.get(0));
    }

    /** 
     * 
     * Notifies a peer, letting him know that this node is it's predecessor
    */
    public void notifyPredecessor(ChordNode successor) throws Exception {
        SSLClient client = new SSLClient(successor.getInetSocketAddress().getHostName(), successor.getPort());
        client.write(messageFactory.notifyPredecessorMessage(self.getId()));
        client.shutdown();
    }

    /**
     * Daniel
     */
    public void checkPredecessor() throws Exception {
        SSLClient client = new SSLClient(predecessor.getInetSocketAddress().getHostName(), predecessor.getPort());
        
        if (!client.connect()) predecessor = null;
    }

    /**
     * Gets the closest preceding node to the key k that this node knows of
     */
    public ChordNode closestPrecedingNode(int k) {
        for (int i = this.m - 1; i >= 0; i--) {
            ChordNode finger = fingerTable.get(i);
            int fingerId = finger.getId();
            if (fingerId > self.getId()  && fingerId <= k) return finger;
        }
        return self;
    }

    /**
     * Finds who holds or will hold the value of a given key.
     * @throws Exception
     */
    public ChordNode lookup(int k) throws Exception {

        // this is important because the successor is the only node whose position the current node knows with certainty
        if (k > self.getId() && k <= fingerTable.get(0).getId()) return fingerTable.get(0);
        
        ChordNode closestPreceding = this.closestPrecedingNode(k);

        return lookup(closestPreceding.getInetSocketAddress(), k);
    }

    private ChordNode lookup(InetSocketAddress peerAddress, int k) throws Exception {
        SSLClient client = new SSLClient(peerAddress.getHostString(), peerAddress.getPort());
        client.write(messageFactory.getLookupMessage(self.getId(), k));
        
        client.read();

        return null; // TODO change to the response of the LOOKUP message
    }
}

