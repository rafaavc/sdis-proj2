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
    private ChordNode successor;
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

        boolean turnoff = true;

        /*if (preexistingNode == null)*/ id = this.generateNodeId(peerAddress);
        //else id = this.getCollisionFreeId(peerAddress, preexistingNode);

        this.self = new ChordNode(peerAddress, id);
        this.m = 32; // an integer has 32 bits
        this.configuration = configuration;
        this.messageFactory = new MessageFactory(configuration.getProtocolVersion());

        if (preexistingNode == null) this.create();
        else {
            if (turnoff) this.successor = new ChordNode(preexistingNode, 111);
            else this.join(preexistingNode);
        }
        
        if (!turnoff) {
            configuration.getThreadScheduler().scheduleWithFixedDelay(() -> {
                try {
                    updateFingers();
                } catch(Exception e) {
                    Logger.error(e, true);
                }
            }, configuration.getRandomDelay(1000, 100), 500, TimeUnit.MILLISECONDS);
            configuration.getThreadScheduler().scheduleWithFixedDelay(() -> {
                try {
                    stabilize();
                } catch(Exception e) {
                    Logger.error(e, true);
                }
            }, configuration.getRandomDelay(1000, 100), 500, TimeUnit.MILLISECONDS);
            // configuration.getThreadScheduler().scheduleWithFixedDelay(() -> {
            //     try {
            //         checkPredecessor();
            //     } catch(Exception e) {
            //         Logger.error(e, true);
            //     }
            // }, 500, 300, TimeUnit.MILLISECONDS);
            configuration.getThreadScheduler().scheduleWithFixedDelay(() -> printFingerTable(), 0, 10, TimeUnit.SECONDS);
        }
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
        return successor;
    }

    public ChordNode getPredecessor() {
        return predecessor;
    }

    private void create() {
        Logger.debug(DebugType.CHORD, "I am GOD! Heaven is " + self);

        this.predecessor = null;
        successor = self;
    }

    private void join(InetSocketAddress preexistingNode) throws Exception {
        this.predecessor = null;

        // send LOOKUP message to the preexisting node
        // and set the successor to the value of the return
        successor = this.lookup(preexistingNode, self.getId()).get();
        Logger.debug(DebugType.CHORD, "Joining chord ring. My successor is " + successor);
    }

    public int getFingerTableIndexId(int idx) {
        return getId() + (int) Math.pow(2, idx);
    }

    /**
     * Checks if id is between the idLeft and idRight
     * @param id
     * @param idLeft
     * @param idRight
     * @return whether id is after idLeft and before idRight in cordRing
     */
    public boolean isBetween(int id, int idLeft, int idRight, boolean inclusiveRight) {
        if (inclusiveRight) {
            if (idLeft <= idRight) return id > idLeft && id <= idRight;
            return id > idLeft || id <= idRight;
        }

        if (idLeft <= idRight) return id > idLeft && id < idRight;
        return id > idLeft || id < idRight;
    }

    public boolean isBetween(int id, ChordNode left, ChordNode right, boolean inclusiveRight) {
        return isBetween(id, left.getId(), right.getId(), inclusiveRight);
    }

    public boolean isBetween(ChordNode compared, ChordNode left, ChordNode right, boolean inclusiveRight) {
        return isBetween(compared.getId(), left, right, inclusiveRight);
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
                if (fingerTable.size() == 0) fingerTable.add(fingerValue);
                else
                {
                    fingerTable.get(nextFingerToFix - 1); // if it's filled up to this point
                    fingerTable.add(fingerValue);
                }
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

        if (successor.getId() == getId()) return;
        
        SSLClient client = new SSLClient(successor.getInetAddress().getHostAddress(), successor.getPort());
        client.connect();

        Message reply = client.sendAndReadReply(messageFactory.getGetPredecessorMessage(self.getId()));

        try {
            ChordNode predecessorOfSuccessor = reply.getNode();  // if it has no predecessor it will throw exception
            
            if (isBetween(predecessorOfSuccessor, self, successor, false)) {
                successor = predecessorOfSuccessor;
            }
        } catch (Exception e) {}

        client.shutdown();

        notifyPredecessor(successor);
    }

    /** 
     * Notifies the successor, letting him know that this node is its predecessor
     * @param successor the node's successor
    */
    public void notifyPredecessor(ChordNode successor) throws Exception {
        Logger.debug(DebugType.CHORD, "Notifying successor " + successor);

        SSLClient client = new SSLClient(successor.getInetAddress().getHostAddress(), successor.getPort());
        client.connect();
        client.sendAndReadReply(messageFactory.getNotifyMessage(self.getId(), self), false);
        client.shutdown();
    }

    /**
     * Updates the successor of the creator node if it is still the node itself
     * @param successor
     */
    public void updateSuccessor(ChordNode node) {
        if (successor == self) successor = node;
    }

    /**
     * This node is being notified of its predecessor
     * @param predecessor the node's predecessor
     */
    public void notify(ChordNode newPredecessor) {
        // if doesn't have predecessor or the current predecessor is no longer valid
        if (predecessor == null || isBetween(newPredecessor.getId(), predecessor.getId(), self.getId(), false)) 
        {
            Logger.debug(DebugType.CHORD, "Changed predecessor to " + newPredecessor.toString());
            predecessor = newPredecessor;
        }

        updateSuccessor(newPredecessor);
    }

    /**
     * Checks whether predecessor has failed
     */
    public void checkPredecessor() throws Exception {
        if (predecessor == null) return;

        SSLClient client = new SSLClient(predecessor.getInetAddress().getHostAddress(), predecessor.getPort());
        
        if (!client.connect()) predecessor = null;
        else client.shutdown();
    }

    /**
     * Gets the closest preceding node to the key k that this node knows of
     */
    public ChordNode closestPrecedingNode(int k) {
        for (int i = this.m - 1; i >= 0; i--) 
        {
            if (i > fingerTable.size() - 1) continue;

            ChordNode finger = fingerTable.get(i);
            int fingerId = finger.getId();

            if (isBetween(fingerId, self.getId(), k, false)) return finger;
        }
        return self;
    }

    /**
     * Finds who holds or will hold the value of a given key.
     * @throws Exception
     */
    public Future<ChordNode> lookup(int k) throws Exception {

        if (fingerTable.size() == 1 || isBetween(k, self, successor, true)) 
        {
            CompletableFuture<ChordNode> future = new CompletableFuture<>();
            future.complete(successor);
            return future;
        }

        ChordNode closestPreceding = this.closestPrecedingNode(k);
        if (closestPreceding.getId() == getId())
        {
            CompletableFuture<ChordNode> future = new CompletableFuture<>();
            future.complete(self);
            return future;
        }

        Logger.debug(DebugType.CHORD, "Looking up key " + k + " to peer " + closestPreceding.getId());
        return lookup(closestPreceding.getInetSocketAddress(), k);
    }

    public Future<ChordNode> lookup(InetSocketAddress peerAddress, int k) throws Exception {
        Logger.debug(DebugType.CHORD, "Using " + peerAddress.getAddress().getHostAddress() + ":" + peerAddress.getPort() + " to LOOKUP!");
        SSLClient client = new SSLClient(peerAddress.getAddress().getHostAddress(), peerAddress.getPort());
        client.connect();

        CompletableFuture<ChordNode> future = new CompletableFuture<>();

        while (!future.isDone()) {
            Logger.debug(DebugType.CHORD, "Sending LOOKUP of key " + k + " to " + peerAddress.toString());
            
            Message reply = client.sendAndReadReply(messageFactory.getLookupMessage(self.getId(), k));
            
            future.complete(reply.getNode());
        }

        client.shutdown();

        return future;
    }

    public void printFingerTable() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n----------------------------------------------\n");
        builder.append("%% Finger table of node " + getId() + " %%\n");
        builder.append("Predecessor = " + predecessor + "\n");
        builder.append("Successor = " + successor + "\n");
        for (int i = 0; i < fingerTable.size(); i++) {
            ChordNode node = fingerTable.get(i);
            builder.append(getFingerTableIndexId(i) + ": " + node.getId() + " | " + node.getInetSocketAddress().toString() + "\n");
        }
        builder.append("----------------------------------------------\n\n");
        Logger.log(builder.toString());
    }
}

