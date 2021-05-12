package chord;

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import utils.Logger;

public class Chord {
    private final InetAddress peerAddress;
    private final BigInteger id;

    /**
     * Instantiates the chord algorithm for this peer, making him join the P2P network
     * @param peerAddress the address of the peer that this class is in respect to.
     * @param otherPeerAddress the address of a peer that already belongs to the P2P network.
     * @throws NoSuchAlgorithmException
     */
    public Chord(InetAddress peerAddress, int port, InetAddress otherPeerAddress) throws NoSuchAlgorithmException {
        // to initiate chord it is needed to have another peer's address (ip:port) 
        // then initiate a connection and send a join request
        this.peerAddress = peerAddress;

        String original = peerAddress.getHostAddress() + port;

        // look more into consistent hashing
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        this.id = new BigInteger(digest.digest(original.getBytes()));

        Logger.log("Got the hash: " + id);
    }

    /**
     * Daniel
     */
    public void updateFingers() {

    }

    /**
     * Daniel
     * Verifies if the predecessor of the node's successer is still the node itself
     */
    public void stabilize() {
        
    }

    /** 
     * Daniel
     * Notifies a peer, letting him know that this node is it's predecessor
    */
    public void notifyPredecessor() {

    }

    /**
     * Daniel
     */
    public void checkPredecessor() {

    }

    /**
     * Rafael
     * Integrates a new peer into the P2P network.
     */
    public void integrateNewPeer(String peerId, InetAddress peerAddress, int port) {
        // needs the peer's id (hash) and insert it in between two other peers (its predecessor and successor)
        // also needs to change those two peers' finger tables (they will handle that)
        // this request should be forwarded to the peer with highest id
        
    }

    /**
     * Removes the peer from the P2P network.
     */
    public void leave() {

    }

    /**
     * Rafael
     */
    public void closestPrecedingNode() {

    }

    /**
     * Rafael
     * Finds who holds or will hold the value of a given key.
     */
    public void lookup() {
        // if this peer holds the value of the key, stop
        // else, forward the request to the closest predecessor of the key that we know of (if none, forward to our successor)
        // if no node has an id equal to the key, the key's value is stored in the node with the next highest id
    }
}

