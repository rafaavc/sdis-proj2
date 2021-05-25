package chord;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import configuration.PeerConfiguration;
import messages.Message;
import messages.MessageFactory;
import messages.MessageParser;
import sslengine.SSLClient;

import java.lang.Math;

import utils.Logger;
import utils.Logger.DebugType;

public class Chord {
    private final List<ChordNode> fingerTable = new ArrayList<>();
    private final PeerConfiguration configuration;
    private final ChordNode self;
    private final int m;
    private final MessageFactory messageFactory;
    private ChordNode predecessor;
    private int nextFingerToFix = -1;

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

        if (preexistingNode == null) this.create();
        else this.join(preexistingNode);

        configuration.getThreadScheduler().scheduleWithFixedDelay(() -> {
            try {
                updateFingers();
            } catch(Exception e) {
                Logger.error(e, true);
            }
        }, 50, 200, TimeUnit.MILLISECONDS);
        // configuration.getThreadScheduler().scheduleWithFixedDelay(() -> {
        //     try {
        //         stabilize();
        //     } catch(Exception e) {
        //         Logger.error(e, true);
        //     }
        // }, 500, 300, TimeUnit.MILLISECONDS);
        configuration.getThreadScheduler().scheduleWithFixedDelay(() -> printFingerTable(), 0, 10, TimeUnit.SECONDS);
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
            ChordNode respectiveNode = this.lookup(preexistingPeerAddress, id).get();  // Send lookup to the preexisting node
            
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
    
    public ChordNode getSuccessor() {
        return fingerTable.get(0);
    }

    private void create() {
        this.predecessor = null;
        this.fingerTable.add(self);
    }

    private void join(InetSocketAddress preexistingNode) throws Exception {
        this.predecessor = null;

        // send LOOKUP message to the preexisting node
        // and set the successor to the value of the return
        //ChordNode successor = this.lookup(preexistingNode, self.getId());  // TODO change to the value returned by the LOOKUP message
        this.fingerTable.add(new ChordNode(preexistingNode, generateNodeId(preexistingNode)));  // TODO change to the successor returned by lookup
    }

    public int getFingerTableIndexId(int idx) {
        return getId() + (int) Math.pow(2, idx);
    }

    /**
     * Daniel
     * @throws Exception
     */
    public void updateFingers() throws Exception {
        nextFingerToFix = nextFingerToFix + 1;
        if (nextFingerToFix > this.m - 1) nextFingerToFix = 0;

        ChordNode fingerValue = lookup(getFingerTableIndexId(nextFingerToFix)).get();
        try 
        {
            fingerTable.get(nextFingerToFix);  // if already exists
            fingerTable.set(nextFingerToFix, fingerValue);
        } 
        catch (IndexOutOfBoundsException e) 
        {
            try 
            {
                fingerTable.get(nextFingerToFix - 1); // if it's filled up to this point
                fingerTable.add(fingerValue);
            } 
            catch (IndexOutOfBoundsException e1) 
            {
                nextFingerToFix--; // To try to fix this one again
                throw new Exception("updateFingers: finger table was not valid.");
            }
        }
    }

    /**
     * Daniel
     * Verifies if the predecessor of the node's successor is still the node itself
     */
    public void stabilize() throws Exception {
        ChordNode successor = fingerTable.get(0);

        if (successor.getId() == getId()) return;
        
        SSLClient client = new SSLClient(successor.getInetSocketAddress().getHostName(), successor.getPort());
        client.connect();
        client.write(messageFactory.getPredecessorMessage(self.getId()));
        client.read((byte[] data, Integer length) -> {
            Logger.log("Received " + new String(data));
        });
        
        int sucPredId = client.getPeerAppData().getInt();

        client.shutdown();

        if (sucPredId != -1) {
            ChordNode sucPredecessor = lookup(sucPredId).get();

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
        client.connect();
        client.write(messageFactory.getNotifyPredecessorMessage(self.getId()));
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
            if (i > fingerTable.size() - 1) continue;
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
    public Future<ChordNode> lookup(int k) throws Exception {
        
        // this is important because the successor is the only node whose position the current node knows with certainty

        if (fingerTable.size() == 1 || k > self.getId() && k <= fingerTable.get(0).getId()) 
        {
            CompletableFuture<ChordNode> future = new CompletableFuture<>();
            future.complete(fingerTable.get(0));
            return future;
        } 

        ChordNode closestPreceding = this.closestPrecedingNode(k);
        if (closestPreceding.getId() == getId())
        {
            CompletableFuture<ChordNode> future = new CompletableFuture<>();
            future.complete(self);
            return future;
        }

        return lookup(closestPreceding.getInetSocketAddress(), k);
    }

    public Future<ChordNode> lookup(InetSocketAddress peerAddress, int k) throws Exception {
        SSLClient client = new SSLClient(peerAddress.getAddress().getHostAddress(), peerAddress.getPort());
        client.connect();
        client.write(messageFactory.getLookupMessage(self.getId(), k));

        Logger.debug(DebugType.CHORD, "Sending LOOKUP of key " + k + " to " + peerAddress.toString());

        CompletableFuture<ChordNode> future = new CompletableFuture<>();
        
        client.read((byte[] data, Integer length) -> {
            // TODO do this in a better way
            try {
                Message message = MessageParser.parse(data, length);
                Logger.log("Received: " + new String(data));
                future.complete(message.getNode());
            } catch (Exception e) {
                Logger.error(e, true);
                future.cancel(true);
            }
        });
        client.shutdown();

        return future;
    }

    public void printFingerTable() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n----------------------------------------------\n");
        builder.append("%% Finger table of node " + getId() + " %%\n");
        for (int i = 0; i < fingerTable.size(); i++) {
            ChordNode node = fingerTable.get(i);
            builder.append(getFingerTableIndexId(i) + ": " + node.getId() + " | " + node.getInetSocketAddress().toString() + "\n");
        }
        builder.append("----------------------------------------------\n\n");
        Logger.log(builder.toString());
    }
}

