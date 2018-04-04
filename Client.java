import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Timestamp;
import java.util.ArrayList;

public class Client extends UnicastRemoteObject implements Interface, Runnable, Serializable{	
	private static final long serialVersionUID = 1L;
	//machine that contains the client
	private Machine machine;
    private PrintWriter writer;
	
	//constructor of Server
	protected Client(Machine machine,  String url, String filename) throws RemoteException, FileNotFoundException, UnsupportedEncodingException {
		this.machine = machine;
		this.machine.URL = url;		
		writer = new PrintWriter(filename,"UTF-8");
	}
	
	//runnable
	@Override
	public void run() {
		int timeout_count, overCount;
		String output = null;
		Battlefield bf = machine.battlefield;
		
		try {
			Interface server = (Interface) java.rmi.Naming.lookup(machine.Servers_URLs.get(machine.host_id));
			server.register(machine.URL,machine.id,0);
		} catch (MalformedURLException | NotBoundException | RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {	
			timeout_count = 0;
			overCount = 0;
			//runs while the game isn't over and there hasn't been a timeout
			while(machine.gameStarted == false || (timeout_count < machine.timeout && !bf.checkGameOver()) || (overCount < machine.gameOverCount && bf.checkGameOver())){
				if(!machine.crash){
					machine.processStepBuffer();
					
					//prints output into file
					output = machine.getOutput();
					if(output != null)
						writer.println(output);
					
					Thread.sleep(machine.genActionTs());
					//waits if game appears to end to check if it's necessary to do a rollback
					//System.out.println(bf.checkGameOver()+" "+machine.gameStarted);
					if(bf.checkGameOver() && machine.gameStarted){
						overCount++;
						if(timeout_count>0)
							timeout_count--;
					}else if(overCount != 0){
						overCount = 0;
					}
					if(timeout_count == machine.timeout)
						System.out.println("TIME HAS RUNNED OUT");
					
					machine.val_buffer.clientCheckTimeout();
				}/*else {
					System.out.println(machine.gameStarted);
					System.out.println(timeout_count + "," +machine.timeout);
					System.out.println(machine.battlefield.checkGameOver());
					System.out.println(overCount + "," +machine.gameOverCount);
				}*/
			}
			bf.printSurvivingUnits();
			while(output != "END"){
				//prints output into file
				output = machine.getOutput();
				if(output != null && output != "END")
					writer.println(output);
				Thread.sleep(machine.genActionTs());
			}
			writer.close();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//send local event to server
	@Override
	public void sendMessage(PhaseType type, Step s, boolean toCommit, int receiver_id, boolean toServer) {
		if(machine.crash) return;
		
//		try {
//			Interface receiver = (Interface) java.rmi.Naming.lookup(machine.Servers_URLs.get(receiver_id));
//			receiver.receiveMessage(type, s, toCommit, machine.host_id);
//		} catch (RemoteException | MalformedURLException | NotBoundException e) {
//			e.printStackTrace();
//		}
		new Thread(new DelayMessage(type, s, toCommit, machine.host_id, machine.Servers_URLs.get(receiver_id))).start();
	}
	
	//receive remote event from server
	@Override
	public synchronized void receiveMessage(PhaseType type, Step s, boolean toCommit, int host_id) throws RemoteException {
		if(machine.crash) return;
		
		if(s == null)
			machine.val_buffer.addCommit(type, null, toCommit, host_id);
		else
			machine.val_buffer.addCommit(type, new Step(s), toCommit, host_id);
	}

	
	//implemented in server only
	@Override
	public void register(String url, int id, int level) throws RemoteException {
		// TODO Auto-generated method stub
		
	}

	//implemented in server only
	@Override
	public void unregister(String url, int id) throws RemoteException {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void login(ArrayList<Unit> units, ArrayList<String> Clients_URLs, ArrayList<Integer>Clients_IDs,  int id, int timestamp, int ts, boolean gameStarted, boolean gameOver) throws RemoteException{
		System.out.println(machine.URL + " logged on");
		writer.println(machine.URL + " logged on");
		//get time from server in the beginning
		synchronized(this){
			machine.setTimeDiff(ts);
			machine.setTimestamp(timestamp);
			machine.gameStarted = gameStarted;
			
			machine.host_id = id;
			machine.battlefield.gameOver = gameOver;
			machine.finalRollPos = -1;
			machine.finalInvPos = -1;
			machine.step_pos = -1;
		}
		/*System.out.println(machine.gameStarted);
		System.out.println(machine.timeout);
		System.out.println(machine.battlefield.checkGameOver());
		System.out.println(machine.gameOverCount);*/
		synchronized(this){
			machine.battlefield.setUnits(units, id);
		}
		/*System.out.println("GAMESTARTED" + gameStarted);
		System.out.println("GAMEOVER" + gameOver);*/
		synchronized(this){
			machine.battlefield.gameOver = gameOver;
			machine.crash = false;
		}
	}
	
	@Override
	public void crash() {
		machine.crash = true;
		machine.steps = null;
		machine.my_units = null;
		machine.battlefield.units = null;
		machine.battlefield.map = null;
		machine.val_buffer.commits = null;
		machine.gameStarted = false;
		machine.timestamp = 0; machine.ts = 0;
		machine.killed = false;
		machine.step_pos = -1;
		machine.finalInvPos = -1; machine.finalRollPos = -1;
		machine.host_id = 0;
		machine.changing_host = false;
		machine.spawned_clients = 0; machine.registed_clients = 0;
	} 
	
	
	@Override
	public boolean isCrashed() {
		return machine.crash;
	}
	
	@Override
	public void restart() {
		machine.battlefield.units = new ArrayList<Unit>();
		machine.my_units = new ArrayList<Unit>();
		machine.val_buffer.setCommits();
		machine.steps = new ArrayList<Step>();
		machine.step_pos = -1;
		Interface server;
		
		try {
			do{
				server = (Interface) java.rmi.Naming.lookup(machine.Servers_URLs.get(machine.host_id));
				if(server.isCrashed())
					machine.host_id++;
			}while(server.isCrashed());
			server.register(machine.URL,machine.id,0);
		} catch (RemoteException | MalformedURLException | NotBoundException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean isGameOver() {
		return this.machine.battlefield.checkGameOver();
	}
	
	@Override
	public ArrayList<Unit> getBattlefield() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}
}
