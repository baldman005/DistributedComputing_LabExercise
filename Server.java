import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

//class Server
public class Server extends UnicastRemoteObject implements Interface, Runnable, Serializable{	
	private static final long serialVersionUID = 1L;
	//list of clients that this server handles
	private ArrayList<String> Clients_URLs;
	private ArrayList<Integer> Clients_IDs;
	private int Dragon_N;
	private int MinPlayer_N;
	//stats about the Dragon units
	public final int MIN_DHP = 50;
	public final int MAX_DHP = 100;
	public final int MIN_DAP = 5;
	public final int MAX_DAP = 20;
	//machine that contains the server
	private Machine machine;
	private PrintWriter writer;
	//stats about the Players units
	public final int MIN_PHP = 10;
	public final int MAX_PHP = 20;
	public final int MIN_PAP = 1;
	public final int MAX_PAP = 10;
	
	//constructor of Server
	protected Server(int Dragon_N, int MinPlayer_N, Machine machine, String url, String filename) throws RemoteException, FileNotFoundException, UnsupportedEncodingException {
		this.Dragon_N = Dragon_N;
		this.Clients_URLs = new ArrayList<String>();
		this.Clients_IDs = new ArrayList<Integer>();
		this.MinPlayer_N = MinPlayer_N;
		this.machine = machine;
		writer = new PrintWriter(filename,"UTF-8");
		machine.setTimeDiff(1);
		machine.URL = machine.Servers_URLs.get(machine.id);
	}
	
	//runnable
	@Override
	public void run() {
		int HP, AP, x, y, timeout_count, overCount;
		String output = null;
		
		Battlefield bf = machine.battlefield;
		
		try {	
			timeout_count = 0;
			overCount = 0;
			//runs while the game isn't over and there hasn't been a timeout
			while(machine.gameStarted == false || (timeout_count < machine.timeout && !bf.checkGameOver()) || (overCount < machine.gameOverCount && bf.checkGameOver())){
				if(!machine.crash){
					//if(machine.gameStarted && machine.id == 0)
					//	machine.crash = true;
					
					machine.processStepBuffer();
					
					//prints output into file
					output = machine.getOutput();
					if(output != null)
						writer.println(output);
					
					if(machine.id == machine.host_id){
						if(!machine.gameStarted && bf.getUnitsNumber() >= (Dragon_N+MinPlayer_N) && machine.finalRollPos == -1 && !machine.val_buffer.hasMyCommand(CommandType.gameStart)){
							machine.val_buffer.addCommit(PhaseType.request, new Step(new Command(CommandType.gameStart, machine.id),new Command(CommandType.falseStart, machine.id),machine.timestamp),false,machine.host_id);
						}
	
						//spawn a player if it hasn't yet
						if(Clients_IDs.size() > machine.spawned_clients){
							//try to spawn the player
							//find a free spot
							do{
								x = (int)(Math.random()*bf.getWidth());
								y = (int)(Math.random()*bf.getHeight());
							}while(!bf.posFree(x, y));
							
							HP = (int)(Math.random() * (MAX_PHP - MIN_PHP) + MIN_PHP);
							AP = (int)(Math.random() * (MAX_PAP - MIN_PAP) + MIN_PAP);
	
							machine.val_buffer.addCommit(PhaseType.request, new Step(new Command(CommandType.spawn,HP,HP,AP,x,y,UnitType.player, bf.getCurrID(),Clients_IDs.get(machine.spawned_clients)),new Command(CommandType.remove,x,y,Clients_IDs.get(machine.spawned_clients)),machine.timestamp),false,machine.host_id);
							machine.spawned_clients++;
						}
						
						//spawns the Units
						if(!machine.gameStarted && bf.getUnitsNumber() < (Dragon_N + MinPlayer_N)){	
							if(machine.val_buffer.myCommandNum(CommandType.spawn) < Dragon_N && machine.finalRollPos == -1){
								//find a free spot
								do{
									x = (int)(Math.random()*bf.getWidth());
									y = (int)(Math.random()*bf.getHeight());
								}while(!bf.posFree(x, y));
								
								HP = (int)(Math.random() * (MAX_DHP - MIN_DHP) + MIN_DHP);
								AP = (int)(Math.random() * (MAX_DAP - MIN_DAP) + MIN_DAP);
								
								machine.val_buffer.addCommit(PhaseType.request, new Step(new Command(CommandType.spawn,HP,HP,AP,x,y,UnitType.dragon, bf.getCurrID(),machine.id),new Command(CommandType.remove,x,y,machine.id),machine.timestamp),false,machine.host_id);
							}
						}
						else
							timeout_count++;
					} else if(machine.gameStarted)
						timeout_count++;
					
					Thread.sleep(machine.genActionTs());
	
					//waits if game appears to end to check if it's necessary to do a rollback
					if(bf.checkGameOver() && machine.gameStarted){
						overCount++;
						if(timeout_count>0)
							timeout_count--;
					}else if(overCount != 0){
						overCount = 0;
					}
					
					if(timeout_count == machine.timeout)
						System.out.println("TIME HAS RUNNED OUT");
				}
				else
					break;
			}
			bf.printSurvivingUnits();
			while(output != "END"){
				//prints output into file
				output = machine.getOutput();
				if(output != null && output != "END")
					writer.println(output);
				Thread.sleep(5);
			}
			writer.close();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	//add new client to list and send a copy of battlefield
	@Override
	public synchronized void register(String machine_URL, int id, int level) {
		if(machine.crash) return;
				
		//deal with servers
		if(id < machine.serversN){
			try{
				if(machine.id != machine.host_id){
					Interface myhost = (Interface) java.rmi.Naming.lookup(machine.Servers_URLs.get(machine.host_id));
					myhost.register(machine_URL, id, level+1);
					return;
				}
				
				Interface server = (Interface) java.rmi.Naming.lookup(machine_URL);
				server.login(machine.battlefield.getUnits(),Clients_URLs,Clients_IDs,machine.host_id,machine.timestamp,machine.ts,machine.gameStarted,machine.battlefield.checkGameOver());
			} catch (MalformedURLException | RemoteException
					| NotBoundException e) {
				e.printStackTrace();
			}
			
			System.out.println(machine_URL + " registered");
		 	writer.println(machine_URL + " registered");
		 	return;
		}
	 	
	 	//deal with clients
	 	if(machine.id != machine.host_id && level == 0){
	 		Interface host;
			try {
				host = (Interface) java.rmi.Naming.lookup(machine.Servers_URLs.get(machine.host_id));
				host.register(machine_URL, id, 0);
			} catch (MalformedURLException | RemoteException
					| NotBoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
	 	
	 	if(!Clients_IDs.contains(id) && !Clients_URLs.contains(machine_URL)){
			machine.registed_clients++;
			this.Clients_URLs.add(machine_URL);
			this.Clients_IDs.add(id);
			System.out.println(machine_URL + " registered");
		 	writer.println(machine_URL + " registered");
		}
	 	
	 	if(machine.id != machine.host_id && level != 0)
	 		return;
	 	
	 	//only host does this
		try {
			//register for other clients
			for(int i = 0; i < machine.Servers_URLs.size(); i++){
				if(i == machine.id)
					continue;
				Interface replica = (Interface) java.rmi.Naming.lookup(machine.Servers_URLs.get(i));
				replica.register(machine_URL, id, level+1);
			}
			
			if(machine.id == machine.host_id){
				Interface client = (Interface) java.rmi.Naming.lookup(machine_URL);
				client.login(machine.battlefield.getUnits(),Clients_URLs,Clients_IDs,machine.id,machine.timestamp,machine.ts,machine.gameStarted,machine.battlefield.checkGameOver());
			}
		} catch (MalformedURLException | NotBoundException | RemoteException e) {
			// TODO Auto-generated catch blockse
			e.printStackTrace();
		} 
	}
	
	
	@Override
	public void login(ArrayList<Unit> units, ArrayList<String> Clients_URLs, ArrayList<Integer>Clients_IDs,  int id, int timestamp, int ts, boolean gameStarted, boolean gameOver){
		System.out.println(machine.URL + " logged on");
		writer.println(machine.URL + " logged on");
		//get time from server in the beginning
		machine.crash = false;
		machine.gameStarted = gameStarted;
		machine.battlefield.gameOver = gameOver;
		machine.setTimeDiff(ts);
		machine.setTimestamp(timestamp);
		this.Clients_IDs = new ArrayList<Integer>(Clients_IDs);
		this.Clients_URLs = new ArrayList<String>(Clients_URLs);
		machine.registed_clients = Clients_IDs.size();
		machine.host_id = id;
		machine.battlefield.setUnits(units, id);
	}
	
	//remove client from list
	@Override
	public void unregister(String client_URL, int id) {
		// TODO Auto-generated method stub
		this.Clients_URLs.remove(client_URL);
		this.Clients_IDs.remove(Integer.valueOf(id));
	}

	//receive remote event
	@Override
	public synchronized void receiveMessage(PhaseType type, Step s, boolean toCommit, int host_id) {
		if(machine.crash) return;
		
		if(s == null)
			machine.val_buffer.addCommit(type, null, toCommit, host_id);
		else
			machine.val_buffer.addCommit(type, new Step(s), toCommit, host_id);
	}

	//send local event to machine
	@Override
	public void sendMessage(PhaseType type, Step s, boolean toCommit, int receiver_id, boolean toServer) {
		if(machine.crash) return;
		
//		try {
//			if(toServer){
//				Interface receiver = (Interface) java.rmi.Naming.lookup(machine.Servers_URLs.get(receiver_id));
//				receiver.receiveMessage(type, s, toCommit, machine.host_id);
//				//new Thread(new DelayMessage(type, s, toCommit, machine.host_id, machine.Servers_URLs.get(receiver_id))).start();
//			}else{
//				//send to all clients
//				for(int i = 0; i < Clients_URLs.size(); i++){
//					Interface receiver = (Interface) java.rmi.Naming.lookup(Clients_URLs.get(i));
//					receiver.receiveMessage(type, s, toCommit, machine.host_id);
//					//new Thread(new DelayMessage(type, s, toCommit, machine.host_id, Clients_URLs.get(i))).start();
//				}
//			}
//		} catch (RemoteException | MalformedURLException | NotBoundException e) {
//			e.printStackTrace();
//		}
		
		if(toServer){
			new Thread(new DelayMessage(type, s, toCommit, machine.host_id, machine.Servers_URLs.get(receiver_id))).start();
		}
		else{
			for(int i = 0; i < Clients_URLs.size(); i++){
				new Thread(new DelayMessage(type, s, toCommit, machine.host_id, Clients_URLs.get(i))).start();
			}
		}
	}  
	
	@Override
	public void crash() {
		machine.crash = true;
//		machine.my_units = null;
//		machine.battlefield.units = null;
//		machine.battlefield.map = null;
//		machine.val_buffer.commits = null;
//		machine.steps = null;
//		machine.gameStarted = false;
//		machine.timestamp = 0; machine.ts = 0;
//		machine.killed = false;
//		machine.finalInvPos = -1; machine.finalRollPos = -1;
//		machine.step_pos = -1;
//		machine.host_id = 0;
//		machine.changing_host = false;
//		machine.spawned_clients = 0; machine.registed_clients = 0;
	} 
	
	@Override
	public boolean isCrashed() {
		return machine.crash;
	}
	
	@Override
	public void restart() {
		machine.my_units = new ArrayList<Unit>();
		machine.battlefield.units = new ArrayList<Unit>();
		machine.val_buffer.setCommits();
		machine.steps = new ArrayList<Step>();
		machine.step_pos = -1;
		Interface server;
		
		if(machine.id == machine.host_id)
			if(machine.host_id == machine.serversN)
				machine.host_id = 0;
			else
				machine.host_id++;
		
		try {
			do{
				server = (Interface) java.rmi.Naming.lookup(machine.Servers_URLs.get(machine.host_id));
				if(server.isCrashed())
					machine.host_id++;
			}while(server.isCrashed());
			server.register(machine.URL,machine.id,0);
		} catch (RemoteException | MalformedURLException | NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean isGameOver() {
		return this.machine.battlefield.checkGameOver();
	}
	
	@Override
	public ArrayList<Unit> getBattlefield() {
		return new ArrayList<Unit>(machine.battlefield.getUnits());
	}
}
