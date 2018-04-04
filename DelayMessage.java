import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class DelayMessage implements Runnable {

	private String URL;	//URL of the receiver process
	private Step s;	//Message that is going to be sent
	private PhaseType type;
	private boolean toCommit;
	private int host_id;
	private static final int MIN_DELAY = 1;
	private static final int MAX_DELAY = 1;
		
	//constructor of the object Delay_Message_Thread
	public DelayMessage(PhaseType type, Step s, boolean toCommit, int host_id, String URL) {
		this.URL=URL;
		this.s=s;
		this.type = type;
		this.toCommit = toCommit;
		this.host_id = host_id;
		return;
	}
	
	//runnable 
	@Override
	public void run () {		
		Interface receiver;
		/*
		 * Simulates a random delay, with Thread.sleep, and the receiver 
		 * receives the message using method receive_ack in the interface 
		 */
		try {
			int delay = (int)(Math.random() *(MAX_DELAY - MIN_DELAY) + MIN_DELAY);
			Thread.sleep(delay);
			
			receiver = (Interface) java.rmi.Naming.lookup(URL);
			receiver.receiveMessage(type, s, toCommit, host_id);
		} catch (MalformedURLException | RemoteException | NotBoundException | InterruptedException e) {
			e.printStackTrace();
		}

		return;
	}

}