import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.TimeUnit;

import configuration.PeerConfiguration;
import configuration.ProtocolVersion;
import exceptions.ArgsException;
import exceptions.ArgsException.Type;
import sslengine.SSLClient;
import sslengine.queue.MessageQueue;
import utils.Logger;

public class Main {
    public static void main(String[] args) throws Exception {

        PeerConfiguration configuration = parseArgs(args);
        Peer peer = new Peer(configuration);

        Registry registry = LocateRegistry.getRegistry();
        registry.rebind(configuration.getServiceAccessPoint(), (Remote) peer);

        Runtime.getRuntime().addShutdownHook(new Thread() { 
            public void run() {
                Logger.log("Closing SSL server and unbinding from registry..."); 

                configuration.getServer().stop();

                SSLClient.queue.destroy();

                try {
                    if (!configuration.getThreadScheduler().awaitTermination(3, TimeUnit.SECONDS))
                        throw new Exception();
                } catch(Exception e) {
                    configuration.getThreadScheduler().shutdown();
                }

                try {
                    peer.writeState();
                } catch (IOException e1) {
                    Logger.error(e1, true);
                }

                try {
                    registry.unbind(configuration.getServiceAccessPoint());
                    Logger.log("Unbound successfully."); 
                } catch (RemoteException | NotBoundException e) {
                    Logger.error("Error unbinding."); 
                }
            } 
        });
    }

    public static PeerConfiguration parseArgs(String args[]) throws Exception {
        if (args.length != 3 && args.length != 5) throw new ArgsException(ArgsException.Type.ARGS_LENGTH);

        // Need to verify better
        ProtocolVersion protocolVersion = new ProtocolVersion(args[0]);
        if (!protocolVersion.equals("1.0") && ! protocolVersion.equals("1.1")) throw new ArgsException(Type.UNKNOWN_VERSION_NO, protocolVersion.toString());

        int serverPort;

        try {
            serverPort = Integer.parseInt(args[2]);
            if (serverPort <= 0) throw new Exception();
        } catch(Exception e) {
            throw new ArgsException(Type.SERVER_PORT, args[2]);
        }

        String serviceAccessPoint = args[1];

        if (args.length > 3) {
            InetAddress preexistingPeerAddress = InetAddress.getByName(args[3]);

            try 
            {
                int preexistingPeerPort = Integer.parseInt(args[4]);
                if (serverPort <= 0) throw new NumberFormatException();

                return new PeerConfiguration(protocolVersion, serviceAccessPoint, serverPort, new InetSocketAddress(preexistingPeerAddress, preexistingPeerPort));
            } 
            catch(NumberFormatException e) 
            {
                throw new ArgsException(Type.PREEXISTING_PEER_PORT, args[3]);
            }
        }
        
        return new PeerConfiguration(protocolVersion, serviceAccessPoint, serverPort);
    } 
}
