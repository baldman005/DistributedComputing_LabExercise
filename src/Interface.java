import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.Remote;

//Interface that enables processes to communicate
public interface Reception extends Remote {
	public void receive_message(Message m)throws java.rmi.RemoteException, InterruptedException, MalformedURLException, NotBoundException;
	public void receive_ack(Ack a) throws java.rmi.RemoteException;
}
