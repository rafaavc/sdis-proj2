package chord;

import java.net.InetAddress;
import utils.Logger;

class Chord {
    private final InetAddress peerAddress;

    /**
     * Instantiates the chord algorithm for this peer and 
     * @param peerAddress the address of the peer that this class is in respect to.
     * @param otherPeerAddress the address of a peer that already belongs to the P2P network.
     */
    public Chord(InetAddress peerAddress, int port, InetAddress otherPeerAddress) {
        // to initiate chord it is needed to have another peer's address (ip:port) 
        // then initiate a connection and send a join request
        this.peerAddress = peerAddress;

        String original = peerAddress.getHostAddress() + port;

        // look more into consistent hashing
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] peerId = digest.digest(original.getBytes());

        Logger.log("Got the hash with length " + peerId.length + ": " + peerId);
    }

    /**
     * Integrates a new peer into the P2P network.
     */
    public void integrateNewPeer() {
        // needs the peer's id (hash) and insert it in between two other peers (its predecessor and successor)
        // also needs to change those two peers' finger tables (they will handle that)


    }

    /**
     * Removes the peer from the P2P network.
     */
    public void leave() {

    }

    /**
     * Finds who holds or will hold the value of a given key.
     */
    public void findKey() {
        // if this peer holds the value of the key, stop
        // else, forward the request to the closest predecessor of the key that we know of (if none, forward to our successor)
        // if no node has an id equal to the key, the key's value is stored in the node with the next highest id
    }
}

