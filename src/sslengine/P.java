package sslengine;

import configuration.ClientInterface;
import messages.Message;
import server.Router;
import state.PeerState;
import utils.Logger;
import utils.Result;

import java.nio.channels.SocketChannel;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import javax.net.ssl.SSLEngine;

public class P implements ClientInterface {

    private SSLServer server;
    private SSLClient client;

    private class MyRouter implements Router {
        public void handle(Message message, SocketChannel socket, SSLEngine engine, String address) throws Exception {
            Logger.log("Received from client with address " + address + ":\n" + message.toString());
            server.write(socket, engine, "I am your server".getBytes());
        }
        
        public void handle(byte[] dataReceived, int length, SocketChannel socket, SSLEngine engine, String address) throws Exception {
            Logger.log("Received from client with address " + address + ":\n" + new String(dataReceived));
            server.write(socket, engine, "I am your server".getBytes());
        }
    }

    public P(int serverPort, int clientPort) throws Exception {
        this.server = new SSLServer("TLS", "localhost", serverPort, new MyRouter());
    }

    public static void main(String[] args) throws Exception {

        P peer = new P(Integer.parseInt(args[1]), Integer.parseInt(args[2]));

        Remote stub = (ClientInterface) UnicastRemoteObject.exportObject(peer,0);

        Registry registry = LocateRegistry.getRegistry();
        registry.rebind(args[0], stub);

        Thread thread = new Thread(new ServerThread(peer.server));
        thread.start();
        // this.client.shutdown();
    }

    @Override
    public void hi() throws RemoteException {

    }

    @Override
    public PeerState getPeerState() throws RemoteException {
        return null;
    }

    @Override
    public Result backup(String filePath, int replicationDegree) throws RemoteException {
        return null;
    }

    @Override
    public Result restore(String fileName) throws RemoteException {
        return null;
    }

    @Override
    public Result delete(String fileName) throws RemoteException {
        return null;
    }

    @Override
    public Result reclaim(int kb) throws RemoteException {
        return null;
    }

    @Override
    public void sendMessageToServer(int n) throws RemoteException {
        try {
            this.client = new SSLClient("TLS", "localhost", 8081);
            this.client.connect();
            //this.client.write("Hello");
            this.client.read();
            //this.client.write("asdasdadadads");
            this.client.read();
            //this.client.write("pppppp");
            this.client.read();
            this.client.shutdown();
        } catch(Exception e) {
            Logger.error(e, true);
        }
    }
}
