package configuration;

import state.PeerState;
import utils.Result;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientInterface extends Remote {
    void hi() throws RemoteException;
    PeerState getPeerState() throws RemoteException;
    String getFingerTableString() throws RemoteException;
    Result backup(String filePath, int replicationDegree) throws RemoteException;
    Result restore(String fileName) throws RemoteException;
    Result delete(String fileName) throws RemoteException;
    Result reclaim(int kb) throws RemoteException;
}
