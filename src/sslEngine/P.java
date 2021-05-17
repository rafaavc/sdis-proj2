package sslEngine;

import configuration.ClientInterface;
import state.PeerState;
import utils.Result;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class P implements ClientInterface {

    private SSLServer server;
    private SSLClient client;

    public P(int serverPort, int clientPort) throws Exception {
        this.server = new SSLServer("TLS", "localhost", serverPort);
        this.client = new SSLClient("TLS", "localhost", clientPort);
    }

    public static void main(String[] args) throws Exception {

        P peer = new P(Integer.parseInt(args[1]), Integer.parseInt(args[2]));

        Remote stub = (ClientInterface) UnicastRemoteObject.exportObject(peer,0);

        Registry registry = LocateRegistry.getRegistry();
        registry.rebind(args[0], stub);

        Thread thread = new Thread(new ServerThread(peer.server));
        thread.start();
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
    public void ssl() throws Exception {
        this.client.connect();
        this.client.write("Hello");
        this.client.read();
        this.client.shutdown();
    }
}
