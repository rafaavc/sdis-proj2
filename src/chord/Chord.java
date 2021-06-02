package chord;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import configuration.PeerConfiguration;
import messages.Message;
import messages.MessageFactory;
import sslengine.SSLClient;
import sslengine.SSLPeer;

import java.lang.Math;

import utils.Logger;

public class Chord {
    private final List<ChordNode> fingerTable = new ArrayList<>();
    private final PeerConfiguration configuration;
    private final ChordNode self;
    private final int m;
    private int nextFingerToFix = -1;
    private ChordNode predecessor, successor;
    private List<ChordNode> successorsSuccessors = new ArrayList<>();

    /**
     * Instantiates the chord algorithm for this peer, making him join the P2P network
     * @param configuration the configuration of the peer
     * @param peerAddress the address of the peer that this class is in respect to.
     * @param preexistingNode the address of a peer that already belongs to the P2P network.
     * @throws Exception
     */
    public Chord(PeerConfiguration configuration, InetSocketAddress peerAddress, InetSocketAddress preexistingNode) throws Exception {
        int id = -1;

        boolean turnoff = false;  // if true chord is turned off (useful for testing other stuff)

        this.m = 32; // an integer has 32 bits
        this.configuration = configuration;

        if (preexistingNode == null || turnoff) id = this.generateNodeId(peerAddress);
        else id = this.getCollisionFreeId(peerAddress, preexistingNode);

        this.self = new ChordNode(peerAddress, id);

        if (preexistingNode == null) this.create();
        else {
            if (turnoff) this.successor = new ChordNode(preexistingNode, 111);   // just for debug
            else this.join(preexistingNode);
        }
        
        if (!turnoff) // just for debug
        {
            configuration.getThreadScheduler().scheduleWithFixedDelay(() -> {
                try {
                    updateFingers();
                } catch(Exception e) {
                    Logger.error(e, true);
                    Logger.debug(self, "Got exception in update fingers! " + e.getMessage());
                }
            }, configuration.getRandomDelay(1000, 100), 250, TimeUnit.MILLISECONDS);

            configuration.getThreadScheduler().scheduleWithFixedDelay(() -> {
                try {
                    stabilize();
                } catch(Exception e) {
                    Logger.error(e, true);
                    Logger.debug(self, "Got exception in stabilize! " + e.getMessage());
                }
            }, configuration.getRandomDelay(1000, 100), 250, TimeUnit.MILLISECONDS);

            configuration.getThreadScheduler().scheduleWithFixedDelay(() -> {
                try {
                    stabilize();
                } catch(Exception e) {
                    Logger.error(e, true);
                    Logger.debug(self, "Got exception in stabilize! " + e.getMessage());
                }
            }, configuration.getRandomDelay(1000, 100), 250, TimeUnit.MILLISECONDS);

            configuration.getThreadScheduler().scheduleWithFixedDelay(this::updateSuccessorsSuccessors, configuration.getRandomDelay(2000, 1500), 1000, TimeUnit.MILLISECONDS);

            configuration.getThreadScheduler().scheduleWithFixedDelay(this::checkPredecessor, 500, 1000, TimeUnit.MILLISECONDS);
        }
    }

    public Chord(PeerConfiguration configuration, InetSocketAddress peerAddress) throws Exception {
        this(configuration, peerAddress, null);
    }

    private static int generateId(String original) throws NoSuchAlgorithmException {
        return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(original.getBytes())).getInt();
    }

    private int generateNodeId(InetSocketAddress peerAddress) throws NoSuchAlgorithmException {
        String original = peerAddress.getAddress().getHostAddress() + peerAddress.getPort();
        return generateId(original);
    }

    public static int generateFileId(File file) throws Exception {
        BasicFileAttributes attr = Files.readAttributes(Path.of("../filesystem/" + file.getPath()), BasicFileAttributes.class);

        String original = file.getPath() + attr.lastModifiedTime() + attr.creationTime() + attr.size();
        return generateId(original);
    }

    private int getCollisionFreeId(InetSocketAddress peerAddress, InetSocketAddress preexistingPeerAddress) throws Exception {
        int id = generateNodeId(peerAddress);
        int originalId = id;

        while (true) {
            ChordNode respectiveNode;
            try {
                respectiveNode = lookup(preexistingPeerAddress, id, 1234).get();  // Send lookup to the preexisting node
            } catch(Exception e) {
                Logger.log("Couldn't make lookup of my id (" + id + ")");
                break;
            }
            
            if (respectiveNode.getId() == id)
            {
                id++;
                if (id == originalId)
                    throw new Exception("I can't enter chord ring because there is no free id for me.");
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

    public ChordNode getSelf() {
        return self;
    }

    private void create() {
        Logger.debug(self, "I am GOD: " + self);

        this.predecessor = null;
        successor = self;
    }

    private void join(InetSocketAddress preexistingNode) throws Exception {
        this.predecessor = null;

        // send LOOKUP message to the preexisting node
        // and set the successor to the value of the return
        successor = lookup(preexistingNode, self.getId()).get();
        Logger.debug(self, "Joining chord ring. My successor is " + successor);
    }

    public int getFingerTableIndexId(int idx) {
        return getId() + (int) Math.pow(2, idx);
    }

    /**
     * Checks if id is between the idLeft and idRight
     * @return whether id is after idLeft and before idRight in cordRing
     */
    public static boolean isBetween(int id, int idLeft, int idRight, boolean inclusiveRight) {
        if (inclusiveRight) {
            if (idLeft <= idRight) return id > idLeft && id <= idRight;
            return id > idLeft || id <= idRight;
        }

        if (idLeft <= idRight) return id > idLeft && id < idRight;
        return id > idLeft || id < idRight;
    }

    public static boolean isBetween(int id, ChordNode left, ChordNode right, boolean inclusiveRight) {
        if (left == null || right == null) return false;
        return isBetween(id, left.getId(), right.getId(), inclusiveRight);
    }

    public static boolean isBetween(ChordNode compared, ChordNode left, ChordNode right, boolean inclusiveRight) {
        if (left == null || right == null || compared == null) return false;
        return isBetween(compared.getId(), left, right, inclusiveRight);
    }

    /**
     * Daniel
     * @throws Exception
     */
    public void updateFingers() throws Exception {
//        Logger.debug(self, "Update fingers");

        int originalFingerToFix = nextFingerToFix;
        nextFingerToFix = nextFingerToFix + 1;
        if (nextFingerToFix > this.m - 1) nextFingerToFix = 0;

        ChordNode fingerValue = lookup(getFingerTableIndexId(nextFingerToFix)).get();
        if (fingerValue == null) {
            Logger.error("Lookup returned null in updateFingers!");
            nextFingerToFix = originalFingerToFix;
            return;
        }
        
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
//        Logger.debug(self, "Update fingers ended");
    }

    /**
     * Daniel
     * Verifies if the predecessor of the node's successor is still the node itself
     */
    public void stabilize() throws Exception {
        Logger.debug(self, "Stabilize");

        if (successor == null || successor.getId() == getId()) return;

        Logger.debug(self, "Sending GETPREDECESSOR to " + successor);
        Message reply = SSLClient.sendQueued(configuration, successor, MessageFactory.getGetPredecessorMessage(self.getId()), true).get();

        try {
            ChordNode predecessorOfSuccessor = reply.getNode();  // if it has no predecessor it will throw exception
            Logger.debug(self, "Got PREDECESSOR = " + predecessorOfSuccessor);
            
            if (isBetween(predecessorOfSuccessor, self, successor, false)) {
                successor = predecessorOfSuccessor;
            }
        } catch (Exception e) {
            Logger.debug(self, "Didn't have predecessor yet!");
        }

        notifyPredecessor(successor);
        Logger.debug(self, "Stabilize ended");
    }

    /** 
     * Notifies the successor, letting him know that this node is its predecessor
     * @param successor the node's successor
    */
    public void notifyPredecessor(ChordNode successor) throws Exception {
        Logger.debug(self, "Sending NOTIFY to successor " + successor);

        SSLClient.sendQueued(configuration, successor, MessageFactory.getNotifyMessage(self.getId(), self), false);
    }

    /**
     * Updates the successor of the creator node if it is still the node itself
     * @param node the successor
     */
    public void updateSuccessor(ChordNode node) {
        if (successor == self) successor = node;
    }

    /**
     * This node is being notified of its predecessor
     * @param newPredecessor the node's predecessor
     */
    public void notify(ChordNode newPredecessor) {
        Logger.debug(self, "Received NOTIFY" + newPredecessor);
        // if doesn't have predecessor or the current predecessor is no longer valid
        if (predecessor == null || isBetween(newPredecessor.getId(), predecessor.getId(), self.getId(), false)) 
        {
            Logger.debug(self, "Changed predecessor to " + newPredecessor.toString());
            predecessor = newPredecessor;
        }

        updateSuccessor(newPredecessor);
    }

    /**
     * Checks whether predecessor has failed
     */
    public void checkPredecessor() {
        if (predecessor == null) return;
        Logger.debug(self, "Checking predecessor...");
        
        if (!SSLPeer.isAlive(predecessor.getInetSocketAddress()))
        {
            predecessor = null;
            Logger.debug(self, "Predecessor was not alive!");
        }
        else Logger.debug(self, "Predecessor was alive!");
    }

    /**
     * Gets the closest preceding node to the key k that this node knows of
     */
    public ChordNode closestPrecedingNode(int k) {
        for (int i = this.m - 1; i >= 0; i--) 
        {
            if (i > fingerTable.size() - 1) continue;

            ChordNode finger = fingerTable.get(i);

            if (finger == null) continue;
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

        Logger.debug(self, "Looking up key " + k + " to peer " + closestPreceding.getId());
        return lookup(closestPreceding, k);
    }

    public Future<ChordNode> lookup(ChordNode node, int k, int id) throws Exception {
        Logger.debug(self, "Using " + node.getInetSocketAddress().getAddress().getHostAddress() + ":" + node.getInetSocketAddress().getPort() + " to LOOKUP!");

        CompletableFuture<ChordNode> future = new CompletableFuture<>();
        Message message = MessageFactory.getLookupMessage(id, k);

        int ntries = 0;
        while ((ntries < 3 || id != 1234) && !future.isDone()) { // only sends exception when the id is 1234
            Logger.debug(self, "Sending LOOKUP of key " + k + " to " + node.getInetSocketAddress());

            Future<Message> f = SSLClient.sendQueued(configuration, node, message, true);
            try {
                Message reply = f.get();
                future.complete(reply == null ? null : reply.getNode());
            } catch (ExecutionException e) {
                ntries++;
            }
        }

        if (!future.isDone()) future.completeExceptionally(new Exception("Couldn't complete lookup of key '" + k + "' to peer " + node.getInetSocketAddress() + "."));

        return future;
    }

    public Future<ChordNode> lookup(InetSocketAddress address, int k, int id) throws Exception {
        Logger.debug(self, "Using " + address.getAddress().getHostAddress() + ":" + address.getPort() + " to LOOKUP!");

        CompletableFuture<ChordNode> future = new CompletableFuture<>();
        Message message = MessageFactory.getLookupMessage(id, k);

        int ntries = 0;
        while ((ntries < 3 || id != 1234) && !future.isDone()) { // only sends exception when the id is 1234
            Logger.debug(self, "Sending LOOKUP of key " + k + " to " + address);

            Future<Message> f = SSLClient.sendQueued(configuration, address, message, true);
            try {
                Message reply = f.get();
                future.complete(reply == null ? null : reply.getNode());
            } catch (ExecutionException e) {
                ntries++;
            }
        }

        if (!future.isDone()) future.completeExceptionally(new Exception("Couldn't complete lookup of key '" + k + "' to peer " + address + "."));

        return future;
    }

    public Future<ChordNode> lookup(ChordNode node, int k) throws Exception {
        return lookup(node, k, self.getId());
    }

    public Future<ChordNode> lookup(InetSocketAddress node, int k) throws Exception {
        return lookup(node, k, self.getId());
    }

    private void removeFromFingerTable(ChordNode node) {
        for (int i = 0; i < fingerTable.size(); i++) {
            if (fingerTable.get(i) != null && fingerTable.get(i).getId() == node.getId()) fingerTable.set(i, null);
        }
    }

    public void updateSuccessorsSuccessors() {
        if (successor == null) return;
        List<ChordNode> newSuccessorsSuccessors = new ArrayList<>();
        for (int i = -1; i < 4; i++)
        {
            ChordNode node = i == -1 ? successor : newSuccessorsSuccessors.get(i);
            try {
                Message reply = SSLClient.sendQueued(configuration, node, MessageFactory.getGetSuccessorMessage(getId()), true).get();
                if (reply == null || reply.getNode().getId() == getId()) break;
                newSuccessorsSuccessors.add(reply.getNode());
            }
            catch(Exception e)
            {
                Logger.error("updating successor's successors (couldn't get successor of " + node +  ")", e, false);
                break;
            }
        }
        synchronized(this) {
            successorsSuccessors = newSuccessorsSuccessors;
        }
    }

    public void peerIsDown(ChordNode node) {
        Logger.debug(self, "In peerIsDown (" + node + ")");
        if (successor != null && successor.getId() == node.getId())
        {
            synchronized(this) {
                if (successorsSuccessors.size() != 0)
                {
                    successor = successorsSuccessors.get(0);
                    successorsSuccessors.remove(successorsSuccessors.get(0));
                }
                else successor = self;
            }
        }
        removeFromFingerTable(node);

        if (predecessor != null && predecessor.getId() == node.getId()) predecessor = null;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n----------------------------------------------\n");
        builder.append("%% Finger table of node ").append(getId()).append(" %%\n");
        builder.append("Predecessor = ").append(predecessor).append("\n");
        builder.append("Successor = ").append(successor).append("\n");
        builder.append("Successor's successors = ");
        synchronized(this) {
            for (ChordNode node : successorsSuccessors) builder.append(node.getId()).append(" | ");
        }
        builder.append("\n");

        for (int i = 0; i < fingerTable.size(); i++) {
            ChordNode node = fingerTable.get(i);
            if (node != null) builder.append(getFingerTableIndexId(i)).append(": ").append(node.getId()).append(" | ").append(node.getInetSocketAddress().toString()).append("\n");
            else builder.append(getFingerTableIndexId(i)).append(": null\n");
        }
        builder.append("----------------------------------------------\n\n");
        return builder.toString();
    }
}

