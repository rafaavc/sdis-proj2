package sslengine;

import configuration.ClientInterface;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Application {

    public static void main(String[] args) throws Exception {

        Registry registry = LocateRegistry.getRegistry();
        ClientInterface stub = (ClientInterface) registry.lookup(args[0]);

        stub.ssl();
    }
}
