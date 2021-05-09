import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import utils.Result;
import configuration.ClientInterface;
import exceptions.ArgsException;
import exceptions.ArgsException.Type;
import utils.Logger;

public class TestApp {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            Logger.error("I need the peer's rmi registry name and the method to invoke.");
            System.exit(1);
        }

        Registry registry = LocateRegistry.getRegistry();
        try {
            ClientInterface stub = (ClientInterface) registry.lookup(args[0]);
            Result result = null;
            Logger.log("Executing command...");

            switch(args[1].toUpperCase()) {
                case "HI":
                    stub.hi();
                    break;
                case "BACKUP": 
                    if (args.length < 4) {
                        Logger.error("To backup I need the file path and the desired replication degree (from 1 to 9, inclusive).");
                        System.exit(1);
                    }
                    try {
                        int desiredReplicationDegree = Integer.parseInt(args[3]);
                        if (desiredReplicationDegree < 1 || desiredReplicationDegree > 9) throw new ArgsException(Type.REPLICATION_DEG, String.valueOf(desiredReplicationDegree));
                        
                        result = stub.backup(args[2], desiredReplicationDegree);
                        if (result == null) Logger.error("[FAILURE]");

                    } catch(NumberFormatException e) {
                        Logger.error("The desired replication degree is not valid. It must be an integer in the inclusive range of 1 to 9.");
                        System.exit(1);
                    }
                    break;
                case "DELETE":
                    if (args.length < 3) {
                        Logger.error("To delete I need the name of the file.");
                        System.exit(1);
                    }
                    result = stub.delete(args[2]);
                    if (result == null) Logger.error("[FAILURE]");
                    break;
                case "RESTORE":
                    if (args.length < 3) {
                        Logger.error("To restore I need the name of the file.");
                        System.exit(1);
                    }
                    result = stub.restore(args[2]);
                    if (result == null) Logger.error("[FAILURE]");
                    break;
                case "RECLAIM":
                    if (args.length < 3) {
                        Logger.error("To reclaim I need the maximum storage allowed.");
                        System.exit(1);
                    }
                    result = stub.reclaim(Integer.parseInt(args[2]));
                    if (result == null) Logger.error("[FAILURE]");
                    break;
                case "STATE": 
                    Logger.log(stub.getPeerState().toString());
                    break;
                default:
                    Logger.error("The operation '" + args[1] + "' doesn't exist.");
                    break;
            }

            if (result != null && result.success()) 
            {
                Logger.log("[SUCCESS] " + result.getMessage());
                return;
            } 
            else if (result != null)
            {
                Logger.log("[FAILURE] " + result.getMessage());
            }
        } catch(NotBoundException e) {
            Logger.error("Could not find peer with access point '" + args[0] + "'.");
            System.exit(1);
        } catch(ArgsException e) {
            Logger.error(e, false);
            System.exit(1);
        } catch(Exception e) {
            Logger.error(e, true);
        }
    }
}
