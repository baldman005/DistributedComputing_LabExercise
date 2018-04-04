import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
	//function to find out whether the simulation has stopped(false) or not(true)
	private static boolean SimulationRunning(ArrayList<Thread> t){
		//UPDATE
		for(int i=0; i<t.size(); i++)
			if(t.get(i).isAlive())
				return true;
		
		return false;
	}
	
	private static ArrayList<String> Servers_URLs = new ArrayList<String>();
	private static ArrayList<String> Clients_URLs = new ArrayList<String>();
	private static final int MAP_WIDTH = 25;
	private static final int MAP_HEIGHT = 25;
	private static final int Dragons_N = 1;
	private static final int MinPlayer_N = 5;
	private static final int time_scale = 1;
	private static final boolean toCrash = false;
	
	public static void main(String args[]) throws FileNotFoundException, UnsupportedEncodingException   {		
		int Servers_N = 5;			//Number of servers
		int Clients_N = MinPlayer_N;//Number of Clients
		int host_id = 0;			//Host server id
		String line = "";			//initial value for line read from file
		long [] start = new long [100]; //array that contains the first timestamp of each player 
		long [] end = new long [100];  // array that contain the last timestamp of each player
		int j =0;
		ArrayList<String> crashedClients = new ArrayList<String>();
		ArrayList<String> crashedServers = new ArrayList<String>();
				
		//if no valid host_id is given, the default is 0
		if(host_id > Servers_N-1 || host_id < 0){
			host_id = 0;
		}
		
		//adds the Servers URLs to the list
		for(int i=0; i < Servers_N; i++){
			Servers_URLs.add("rmi://localhost:" + (1099+i) + "/s" + i);
		}
		
		//adds the Clients URLs to the list
		for(int i=0; i < Clients_N; i++){
			Clients_URLs.add("rmi://localhost:" + (1099+Servers_N+i) + "/c" + i);
		}
				
		ArrayList<Thread> t = new ArrayList<Thread>();
		
		//read first and last timestamp from file
		try (BufferedReader br = new BufferedReader(new FileReader("gametrace.txt"))){
			while ((line = br.readLine()) != null){
				String[] times = line.split(",");
				start[j] = Long.parseLong(times[0]);
				end[j++] = Long.parseLong(times[1]) ;
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		//create arrays that contain difference in seconds from minimum timestamp read from file 
		long timeZero = Arrays.stream(start).min().getAsLong();
		long [] spawnTimes = new long [100];
		long [] crashTimes = new long [100];
		
		for (int i=0; i < start.length; i++) {
			spawnTimes[i] = start[i] - timeZero;
			crashTimes[i] = end[i] - timeZero; 
		}

		//sort these arrays
		Arrays.sort(spawnTimes);
		Arrays.sort(crashTimes);
		
		
		try {	
		//creates the servers
		ArrayList<Server> servers = new ArrayList<Server>();
		for(int i=0; i<Servers_N; i++){
			Machine machine = new Machine(i, MAP_WIDTH, MAP_HEIGHT, Servers_URLs.get(i), Servers_N,0,1,host_id, Servers_URLs);
			servers.add(new Server(Dragons_N, MinPlayer_N, machine,Servers_URLs.get(i),"server"+i+".res"));
		}
		
		//creates the RMI registries
		ArrayList<Registry> reg = new ArrayList<Registry>();
		for(int i=0; i<(Servers_N + Clients_N); i++)
			reg.add( java.rmi.registry.LocateRegistry.createRegistry(1099+i));
		
		//binds a remote reference to the specified name in the registries
		for(int i=0; i<Servers_N; i++)
			reg.get(i).bind(Servers_URLs.get(i), servers.get(i));
		
		//binds the Servers URL to the servers
		for(int i=0; i<Servers_N; i++)
			java.rmi.Naming.bind(Servers_URLs.get(i), servers.get(i));
				
		//creates the clients
		ArrayList<Client> clients = new ArrayList<Client>();
		for(int i=0; i<Clients_N; i++) {
			Machine machine = new Machine(i + Servers_N, MAP_WIDTH, MAP_HEIGHT, Clients_URLs.get(i), Servers_N,0,0,host_id, Servers_URLs);

			clients.add(new Client(machine,Clients_URLs.get(i),"client"+i+".res"));
		}
		
		//binds a remote reference to the specified name in the registries	
		for(int i=0; i<Clients_N; i++)
			reg.get(i+Servers_N).bind(Clients_URLs.get(i), clients.get(i));
		
		//binds the Clients URL to the clients
		for(int i=0; i<Clients_N; i++)
			java.rmi.Naming.bind(Clients_URLs.get(i), clients.get(i));
	    		
	    //creates the threads and starts them
		for(int i=0; i<Servers_N; i++){
			t.add(new Thread(servers.get(i)));
			t.get(i).start();
		}
		
		Thread.sleep(1500);
		
		for(int i=0; i<Clients_N; i++){
			Thread.sleep(100);
			t.add(new Thread(clients.get(i)));
			t.get(i+Servers_N).start();
		}
	    
		
		Thread viewer = new Thread(new BattleFieldViewer(MAP_HEIGHT, MAP_WIDTH, Servers_URLs));
		int k = 0;
		int crashId = 0;
	    //waits for the simulation to end
		Interface target;
		//ArrayList<Unit> bf = null;
		
		boolean isServer = false;
		
		while(SimulationRunning(t)) {
			Thread.sleep(100);
			if(toCrash){
				if (k <= Clients_N + Servers_N - 1) {
					Thread.sleep(crashTimes[k] *time_scale - 100);
					//clients				
					if(!isServer){
						int max = 10*1000;
						int count = 0;
						do{
							count++;
							crashId = (int)Math.floor(Math.random() * Clients_N);
							target = (Interface) java.rmi.Naming.lookup(Clients_URLs.get(crashId));
							if(!SimulationRunning(t))
								break;
						}while(target.isGameOver() && count < max);
						if(!SimulationRunning(t))
							continue;
						if (!crashedClients.contains(Clients_URLs.get(crashId))) {
							crashedClients.add(Clients_URLs.get(crashId));
							try {
								target = (Interface) java.rmi.Naming.lookup(Clients_URLs.get(crashId));
								System.out.println(Clients_URLs.get(crashId)+ " crashed");
								target.crash() ;
							} catch (MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
							}
						}else {
							crashedClients.remove(Clients_URLs.get(crashId));
							try {
								target = (Interface) java.rmi.Naming.lookup(Clients_URLs.get(crashId));
								System.out.println(Clients_URLs.get(crashId)+ " restarts");
								target.restart() ;
							} catch (MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
							}
						} 
					}
					//servers
					else{
						crashId = (int)Math.floor(Math.random() * Servers_N);
						target = (Interface) java.rmi.Naming.lookup(Servers_URLs.get(crashId));
						do{
							crashId = (int)Math.floor(Math.random() * Clients_N);
							target = (Interface) java.rmi.Naming.lookup(Clients_URLs.get(crashId));
						}while(target.isGameOver() && SimulationRunning(t));
						if (!crashedServers.contains(Servers_URLs.get(crashId))) {
							crashedServers.add(Servers_URLs.get(crashId));
							try {
								target = (Interface) java.rmi.Naming.lookup(Servers_URLs.get(crashId));
								System.out.println(Servers_URLs.get(crashId)+ " crashed");
								target.crash() ;
							} catch (MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
							}
						}else {
							crashedServers.remove(Servers_URLs.get(crashId));
							try {
								target = (Interface) java.rmi.Naming.lookup(Servers_URLs.get(crashId));
								System.out.println(Servers_URLs.get(crashId)+ " restarts");
								target.restart() ;
							} catch (MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
							}
						}
					}
					k=k+1;
				}
			}
		}
	    
	    //unbinds the registries references
		for(int i=0; i<Servers_N; i++)
	    	reg.get(i).unbind(Servers_URLs.get(i));
		for(int i=0; i<Clients_N; i++)
			reg.get(i+Servers_N).unbind(Clients_URLs.get(i));
		
		//unexports the registries
		for(int i=0; i<(Servers_N + Clients_N); i++)
			UnicastRemoteObject.unexportObject(reg.get(i),true);

		} catch ( RemoteException| MalformedURLException| 
				AlreadyBoundException | NotBoundException  
				| InterruptedException e) {
			e.printStackTrace();
		}
		//exits to kill the RMI ports
		System.exit(0);		
	}

}
