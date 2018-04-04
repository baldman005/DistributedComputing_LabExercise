import java.io.FileNotFoundException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;

//class machine that saves either the server or the client
public class Machine implements Serializable{
	private static final long serialVersionUID = 1L;
	//Server_URLs
	public ArrayList<String> Servers_URLs;
	public final int MAX_actionTs = 50;
	public final int MIN_actionTs = 20;
	public int id;
	public int timeout = 2*100000/(MIN_actionTs+MAX_actionTs);
	//buffer with commands;
	public ArrayList<Step> steps;
	//curr position in buffer
	public int step_pos = -1;
	private int max_commands = 500;
	public Battlefield battlefield;
	public boolean gameStarted = false;
	//arraylist with the units that the machine will process
	public ArrayList<Unit> my_units;
	//RMI URL of client or server responsible for this machine
	public String owner;
	//buffer with the messages for the file
	public ArrayList<String> outputs;
	private int outputs_pos = -1;
	private int max_outputs = 500;
	//to distinguish ids from servers and clients, saves the number of servers and clients
	public int serversN;
	//the timestamp of the current command
	public int timestamp;
	//the time to add to each new Timestamp
	public int ts;
    //time to wait before game is over
    public final int gameOverCount = 2*200/(MIN_actionTs+MAX_actionTs);
    //boolean that doesn't permit the machine to count timestamp if doing a rollback
    public int finalRollPos = -1;
    public int finalInvPos = -1;
    //boolean to verify if a unit was killed
    public boolean killed;
    //id of the host server (var only relevant for servers)
    public int host_id;
    //object with buffer to process fault tolerance messages
    public FaultTol val_buffer;
    //boolean to simulate crash
  	public boolean crash = true;
    //boolean to know if system is choosing a new host
    public boolean changing_host = false;
    //number of clients spawned (only matters for host)
  	public int spawned_clients = 0;
  	//number of registed clients
  	public int registed_clients = 0;
  	//URL used for registering client to server
  	public String URL;
  	//id of its unit(if a player)
  	public int my_unit_id = -1;
    
	//constructor of Machine
	protected Machine(int id, int MAP_WIDTH, int MAP_HEIGHT, String owner, int serversN, int timestamp, int ts, int host_id, ArrayList<String> Servers_URLs) throws RemoteException, FileNotFoundException, UnsupportedEncodingException {
		this.id = id;
		battlefield = new Battlefield(MAP_WIDTH, MAP_HEIGHT,this);
		my_units = new ArrayList<Unit>();
		steps = new ArrayList<Step>();
		this.owner = owner;
		outputs = new ArrayList<String>();
		this.serversN = serversN;
		this.timestamp = 0;
		this.ts = 0;
		this.host_id = host_id;
		this.val_buffer = new FaultTol(this);
		crash = false;
		this.Servers_URLs = new ArrayList<String>(Servers_URLs);
	}
	
	//set the Timestamp, used after adding each command
	public void setTimestamp(int t){
		this.timestamp = t;
	}
	
	//set the time between timestamps
	public void setTimeDiff(int ts){
		this.ts = ts;
	}
	
	//method to start threads to process units
	public synchronized void unitsStart(){
		for(Unit u:my_units)
			u.startProc();
	}
	
	//pauses the units
	public synchronized void pauseUnits(){
		for(Unit u:my_units)
			u.paused = true;
	}
	
	//method to add string to outputs
	public synchronized void addOutput(String message){
		if(outputs.size() == max_outputs){
			outputs.remove(0);
			if(outputs_pos >= 0)
				outputs_pos--;
		}
		outputs.add(message);
	}
	
	//method to return an output
	public synchronized String getOutput(){
		String out = null;
		
		if((outputs_pos+1) < outputs.size()){
			outputs_pos++;
			out = outputs.get(outputs_pos);
		}
		return out;
	}
	
	//add a remove command
	private synchronized void addRemove(Command normal, int timestamp){
		Unit u = battlefield.getUnit(normal.arg(2), normal.arg(3));

		if(finalRollPos != -1)
			finalRollPos++;
		steps.add(step_pos+1,new Step(new Command(CommandType.remove,u.getX(),u.getY(),normal.getOriginId()),new Command(CommandType.spawn,u.getMaxHP(),u.getHP(),u.getAP(),u.getX(),u.getY(),u.getType(),u.getUnitID(),normal.getOriginId()),timestamp));
		this.killed = false;
	}
	
	//method to process the steps buffer
	public synchronized void processStepBuffer(){
		Step s;
		Unit u = null;
		
		if((step_pos+1) < steps.size() || finalInvPos != -1){
			if(this.crash) return;
			
			if(finalInvPos == -1)
				step_pos++;
			
			if(step_pos+1 > steps.size()) step_pos = steps.size() - 1;
			if(step_pos < 0) step_pos = 0;
			
			s = steps.get(step_pos);
			
			if(s==null){
				System.out.println(this.owner + ": " +"ERROR, command null");
				this.addOutput("ERROR, command null");
				return;
			}
			
			//doing inverse
			if(finalInvPos == -1){	
				if(!s.beenProcessed){
					this.timestamp += this.ts;
				}
				synchronized(this){
					u = s.getNormalCommand().processCommand(battlefield, this, s.getInvCommand(),s.getTimestamp(),step_pos,s.beenProcessed);
				}
				s.beenProcessed = true;
				if(killed)
					addRemove(s.getNormalCommand(),s.getTimestamp());
			} else {
				//stopped inverse and starts normal direction
				if (step_pos == finalInvPos){
					finalInvPos = -1;
					this.timestamp += this.ts;
					System.out.println(this.owner + ": " +"INVERSE ENDED");
					this.addOutput("INVERSE ENDED");
					synchronized(this){
						u = s.getNormalCommand().processCommand(battlefield, this, s.getInvCommand(),s.getTimestamp(),step_pos,s.beenProcessed);
					}
					s.beenProcessed = true;
					if(killed)
						addRemove(s.getNormalCommand(),s.getTimestamp());
				}	
				else{
					if(s.beenProcessed)
						u = s.getInvCommand().processCommand(battlefield, this, null,s.getTimestamp(),step_pos,s.beenProcessed);
					step_pos--;
					if(s.getNormalCommand().getType() == CommandType.remove){
						steps.remove(s);
						finalRollPos--;
					}
				}
			}
			
			if(finalRollPos == step_pos && finalInvPos == -1){
				System.out.println(this.owner + ": " +"ROLLBACK ENDED");
				this.addOutput("ROLLBACK ENDED");
				finalRollPos = -1;
				//resume units
				unitsStart();
			}
			
			//means that this server spawned the unit
			if(u != null){
				my_units.add(u);
				//if the game has already begun start processing the unit
				if(gameStarted && finalRollPos != -1)
					u.startProc();
			}
		}
	}
	
	//adds step to buffer
	public synchronized void addStep(Command normal, Command inverse, int timestamp){
		int i;
		boolean startedRoll = false;
		
		//if buffer is full discard oldest command and adapts the rollback vars
		if(steps.size() == max_commands){
			steps.remove(0);
			step_pos--;
			if(finalRollPos != -1)
				finalRollPos--;
			if(finalInvPos > 0)
				finalInvPos--;
		}
			
		//find correct location for new step
		for(i = steps.size(); i > 0; i--){
			int step_timestamp = steps.get(i-1).getTimestamp();
			
			//means that needs to rollback
			if(i <= step_pos && !startedRoll){
				startedRoll = true;
			}
			
			/*if(normal.getType() == CommandType.spawn && !gameStarted)
				break;*/
			/*if(timestamp == step_timestamp && normal.getOriginId() == steps.get(i-1).getOriginId() && this.gameStarted)
				return;
			else*/ if(normal.getType() != CommandType.spawn && steps.get(i-1).getNormalCommand().getType() == CommandType.gameStart){
				break;	
			}
			else if(normal.getType() == CommandType.gameStart && steps.get(i-1).getNormalCommand().getType() != CommandType.spawn){
				continue;	
			}
			else if(timestamp > step_timestamp || (timestamp == step_timestamp && normal.getOriginId() >= steps.get(i-1).getOriginId())){
				break;	
			}
		}
		
		//new command increased the size
		if(i <= finalRollPos && finalRollPos < steps.size())
			finalRollPos++;
		
		steps.add(i, new Step(new Command(normal),new Command(inverse),timestamp));
		this.timestamp = Math.max(this.timestamp,timestamp);
		if(startedRoll){
			//units pause while rollback
			this.pauseUnits();
			//means it wasn't doing a rollback
			if(finalRollPos == -1){
				System.out.println(this.owner + ": " +"ROLLBACK STARTED");
				this.addOutput("ROLLBACK STARTED");
				finalRollPos = step_pos+1;
			}
			else{
				System.out.println(this.owner + ": " +"INSIDE ROLLBACK STARTED");
				this.addOutput("INSIDE ROLLBACK STARTED");
			}
			
			
			if(i <= step_pos && step_pos < steps.size())
				step_pos++;
			
			if(finalInvPos == -1 || i < finalInvPos)
				finalInvPos = i;
		}
	}
	
	//starts the game
	public void startGame(){
		gameStarted = true;
		unitsStart();
	}
		
	//generate the action delay
	public int genActionTs(){
		return (int)(Math.random() * (MAX_actionTs - MIN_actionTs) + MIN_actionTs);
	}
	
	//send to all the other servers that have id different from ignore_id
	public void sendToOtherServers(PhaseType type, Step s, boolean toCommit, int ignore_id){
		try {
			Interface dest = (Interface) java.rmi.Naming.lookup(this.owner);
			for(int i = 0; i < this.serversN; i++)
				if(i != ignore_id){
					/*if(this.id == this.host_id && type == PhaseType.prepare)
						System.out.println("BLAA " + i);*/
					dest.sendMessage(type, s, toCommit, i, true);
				}
		} catch (RemoteException | MalformedURLException | NotBoundException e) {
			e.printStackTrace();
		}
	}
	
	//send to all the other servers that have id different from ignore_id
	public void sendToAllClients(PhaseType type, Step s, boolean toCommit){
		try {
			Interface dest = (Interface) java.rmi.Naming.lookup(this.owner);
			dest.sendMessage(type, s, toCommit, -1, false);
		} catch (RemoteException | MalformedURLException | NotBoundException e) {
			e.printStackTrace();
		}
	}
	
	//new host adds dragons to its my units and starts threads
	public void dragonsMine(){
		for(Unit u:battlefield.getUnits())
			if(u.getType() == UnitType.dragon){
				my_units.add(u);
				if(gameStarted){
					u.startProc();
				}
			}
		
		this.spawned_clients = this.registed_clients;
	}
}