import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;

//class that deals with the 2 phase variables and methods
public class FaultTol implements Serializable{
	private static final long serialVersionUID = 1L;

	//Commit is a variable that needs to be committed (or aborted) and then goes to machine's steps
	public class Commit implements Serializable{
		private static final long serialVersionUID = 1L;

		public Step step;
		public int commit = 0, abort = 0;
		public int timecount = 0;
		
		//constructor for Commit
		protected Commit(Step step){
			this.step = step;
		}
	}
	
	private Machine machine;
	public ArrayList<Commit> commits; 
	//number of faulty servers
	private int f = 1;
	//count that means the host has died
	private int timecount_death;
	//max size of the buffer
	private int max_size = 100;
	
	//constructor of FaultTol
	protected FaultTol(Machine machine){
		this.machine = machine;
		commits = new ArrayList<Commit>();
		timecount_death = 2*4000/(machine.MIN_actionTs + machine.MAX_actionTs);
	}
	
	//method to see if there is already a commit (only increments counts) or add new one
	private synchronized int processCommit(PhaseType type, Step s, boolean toCommit){
		boolean choice;
		
		if(machine.id == machine.host_id){
			switch(type){
				case request:
					choice = s.getNormalCommand().checkValidity(machine.battlefield);
					break;
				case commit:
					choice = toCommit;
					break;
				default:
					choice = true;
					break;
			}
		} else if(machine.id < machine.serversN){
			switch(type){
				case prepare:
					choice = s.getNormalCommand().checkValidity(machine.battlefield);
					break;
				case commit:
					choice = toCommit;
					break;
				default:
					choice = true;
					break;
			}
		} else{
			choice = toCommit;
		}
		
		for(Commit c:commits){
			if(c.step.getOriginId() == s.getOriginId() && c.step.getTimestamp() == s.getTimestamp() && s.getNormalCommand().sameAs(c.step.getNormalCommand())){					
				if(choice){
					if(type != PhaseType.request){
						c.commit++;
					}
					else{
						c.commit = 1;
						c.abort = 0;
					}
					return c.commit;
				}
				else{
					if(type != PhaseType.request){
						c.abort++;
					}
					else{
						c.commit = 0;
						c.abort = 1;
					}
					return c.abort;
				}
			}
		}
		
		//buffer is full, so remove old messages
		if(commits.size() == max_size)
			commits.remove(0);
		
		//Step doesn't exist yet in list, so add it
		Commit c = new Commit(new Step(s));
		commits.add(c);
			
		if(choice){
			c.commit++;
			return c.commit;
		}
		c.abort++;
		return c.abort;
	}
	
	//decide on the correct value
	private synchronized boolean majority(Step s){
		boolean decision = false;
		
		for(Commit c:commits)
			if(c.step.getOriginId() == s.getOriginId() && c.step.getTimestamp() == s.getTimestamp() && s.getNormalCommand().sameAs(c.step.getNormalCommand()))
				if(c.commit > c.abort)
					decision = true;
				else
					decision = false;
		
		//if(machine.id == 0)
		//	decision = !decision;
		
		return decision;
	}
	
	//when receiving a commit chooses how to proceed
	public synchronized void addCommit(PhaseType type, Step s, boolean toCommit, int host_id) {
		//case of being a server
		if(machine.id < machine.serversN){
			//is the host
			if(machine.id == machine.host_id){
				switch(type){
					case request:
						//add commit (should be new, so check that) and send prepare to replicas
						if(processCommit(type,s,false) != 1){
							System.out.println("ERROR: already existed");
							
							break;
						}
						//if there are no faulty nodes then is ready to send reply 
						if(f == 0){
							machine.sendToAllClients(PhaseType.reply, s, majority(s));
							commitDone(s);
						}

						machine.sendToOtherServers(PhaseType.prepare, s, false, machine.id);
						machine.sendToOtherServers(PhaseType.commit, s, s.getNormalCommand().checkValidity(machine.battlefield), machine.id);
						break;
					case commit:
						//increment count and if equal to 2f+1 send to client (if abort) or all clients (if commit)
						if(processCommit(type,s,toCommit) == 2*f + 1){
							commitDone(s);
							machine.sendToAllClients(PhaseType.reply, s, majority(s));
						}
						
						break;
					default:
						break;
				}
			}else{
				switch(type){
					case commit:
						//increment count and if equal to 2f+1 send to client (if abort) or all clients (if commit)
						if(processCommit(type,s,toCommit) == 2*f + 1){
							//send to all clients
							commitDone(s);
							machine.sendToAllClients(PhaseType.reply, s, majority(s));
						}
						break;
					case prepare:
						//send to all servers a commit
						//machine.sendToOtherServers(PhaseType.commit, s, checkValidity(s.getNormalCommand()), machine.id);
						machine.sendToOtherServers(PhaseType.commit, s, s.getNormalCommand().checkValidity(machine.battlefield), machine.id);
						
						//there can be a big delay in sending prepare and replica receives all commits first, so check here also
						if(processCommit(type,s,false) == 2*f + 1){
							//send to all clients
							commitDone(s);
							machine.sendToAllClients(PhaseType.reply, s,  majority(s));
						}
						break;
					case elect:
						if((machine.host_id !=  machine.serversN -1 && host_id != machine.host_id + 1) || (machine.host_id == machine.serversN -1 && host_id != 0))
							break;
						
						machine.host_id++;
						if(machine.id == machine.host_id){
							machine.dragonsMine();
							machine.sendToAllClients(PhaseType.elect, new Step(new Command(CommandType.falseStart,machine.id),null,-1), false);
						}
					default:
						break;
				}
			}
		}else{
			switch(type){
				case reply:
					//increment count and if equal to f+1 it has received all replies necessary
					if(processCommit(type,s,toCommit) == f + 1){
						commitDone(s);
					}
					break;
				case mine:
					for(Commit c:commits)
						if(c.step.getOriginId() == s.getOriginId() && c.step.getTimestamp() == s.getTimestamp() && s.getNormalCommand().sameAs(c.step.getNormalCommand()))
							return;
					commits.add(new Commit(new Step(s)));
					break;
				case elect:
					machine.host_id = s.getOriginId();
					resendCommit();
					machine.changing_host = false;
				default:
					break;
			}
		}
	}

	//When host changed send the commits that had received no replies
	private synchronized void resendCommit(){
		for (Commit c:commits){
			if(c.commit == 0 && c.abort == 0 && c.timecount != 0){
				c.timecount = 0;
				try {
					Interface dest = (Interface) java.rmi.Naming.lookup(machine.owner);
					dest.sendMessage(PhaseType.request,c.step,false,machine.host_id,false);
				} catch (RemoteException | MalformedURLException | NotBoundException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	//When commit is ready to leave, see if it is to be processed or aborted
	private synchronized Commit commitDone(Step s){
		for(Commit c:commits){
			if(c.step.getOriginId() == s.getOriginId() && c.step.getTimestamp() == s.getTimestamp() && s.getNormalCommand().sameAs(c.step.getNormalCommand())){
				//means it is to be committed
				if(c.commit > c.abort){
					machine.addStep(s.getNormalCommand(), s.getInvCommand(), s.getTimestamp());
					return c;
				}
			}
		}
		return null;
	}
	
	//Check if the buffer has a spawn created by this machine
	public synchronized boolean hasMyCommand(CommandType type){
		for(Commit c:commits){
			if(c.step.getOriginId() == machine.id && c.step.getNormalCommand().getType() == type){
				return true;
			}
		}
		return false;
	}
		
	//Return number of spawns in this buffer
	public synchronized int myCommandNum(CommandType type){
		int count = 0;
		for(Commit c:commits){
			if(c.step.getOriginId() == machine.id && c.step.getNormalCommand().getType() == type){
				count++;
			}
		}
		return count;
	}
	
	//method to increase the timecount of commits that have received no reply (only for player)
	//if timeout than assumes host has died, starts election
	public synchronized void clientCheckTimeout(){
		if(machine.crash) return;
		
		int prev_host_id = machine.host_id;
		
		for (Commit c:commits){
			if(c.commit == 0 && c.abort == 0){
				c.timecount++;
				
				if(c.timecount == this.timecount_death && !machine.changing_host){
					if(machine.host_id == machine.serversN)
						machine.host_id = 0;
					else
						machine.host_id++;
					machine.changing_host = true;
					machine.sendToOtherServers(PhaseType.elect, null, false, prev_host_id);
				}else if(c.timecount % this.timecount_death == 0 && c.timecount > this.timecount_death){
					if(machine.host_id == machine.serversN)
						machine.host_id = 0;
					else
						machine.host_id++;
					machine.sendToOtherServers(PhaseType.elect, null, false, prev_host_id);
				}
			}
		}
	}
	
	//eliminates all the units
	public synchronized void removeAllCommits(){
		for(Commit c:commits)
			commits.remove(c);
	}
	
	//sets commits
	public synchronized void setCommits(){
		this.commits = new ArrayList<Commit>();
	}
	
	//Prints the Commits buffer
	public void printCommit(){
		for (Commit c:commits){
			System.out.println("ORIGIN ID " + c.step.getOriginId());
			System.out.println("TIMESTAMP " + c.step.getTimestamp());
			System.out.println("COMMAND TYPE " + c.step.getNormalCommand().getType());
			System.out.println("COMMITS " + c.commit);
			System.out.println("ABORTS " + c.abort);
			System.out.println("_____________________________");
		}
	}
}