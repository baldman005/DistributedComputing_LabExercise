import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

//Interface that enables processes to communicate
public interface Interface extends Remote {
	public void receiveMessage(PhaseType type, Step s, boolean toCommit, int host_id) throws RemoteException;

	public void register(String url, int id, int level) throws RemoteException;

	public void unregister(String url, int id) throws RemoteException;

	public void sendMessage(PhaseType type, Step s, boolean toCommit, int receiver_id, boolean toServer) throws RemoteException;
	
	public void login(ArrayList<Unit> units, ArrayList<String> Clients_URLs, ArrayList<Integer>Clients_IDs,  int id, int timestamp, int ts, boolean gameStarted , boolean gameOver ) throws RemoteException;

	public void crash() throws RemoteException;
	
	public boolean isCrashed() throws RemoteException;
	
	public boolean isGameOver() throws RemoteException;
	
	public void restart() throws RemoteException;

	public ArrayList<Unit> getBattlefield() throws RemoteException;
}
