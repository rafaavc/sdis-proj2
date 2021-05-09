package configuration;

import java.rmi.Remote;
import java.rmi.RemoteException;

import utils.Result;
import state.PeerState;

public interface ClientInterface extends Remote {
    public void hi() throws RemoteException;
    public PeerState getPeerState() throws RemoteException;
    public Result backup(String filePath, int replicationDegree) throws RemoteException;
    public Result restore(String fileName) throws RemoteException;
    public Result delete(String fileName) throws RemoteException;
    public Result reclaim(int kb) throws RemoteException;
}
