import java.io.Serializable;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;

public class Command implements Serializable {
	private static final long serialVersionUID = 1L;
	private CommandType type;
	private ArrayList<Integer> args_int;
	
	//constructor for gameStart or falseStart
	protected Command(CommandType type, int origin_id){
		this.type = type;
		args_int = new ArrayList<Integer>();
		args_int.add(origin_id);
	}
	
	//constructor with unit to be removed (for remove)
	protected Command(CommandType type, int sourceX,int sourceY, int origin_id){
		this.type = type;
		args_int = new ArrayList<Integer>();
		args_int.add(sourceX);
		args_int.add(sourceY);
		args_int.add(origin_id);
	}
	
	//constructor 1 value of coordinates, currHP, maxHP, AP, the unit type (0 dragon, 1 player)(for spawn), and the id of the client/server
	protected Command(CommandType type, int currHP, int maxHP, int AP, int x, int y, UnitType unittype, int unit_id, int origin_id){
		this.type = type;
		args_int = new ArrayList<Integer>();
		args_int.add(currHP);
		args_int.add(maxHP);
		args_int.add(AP);
		args_int.add(x);
		args_int.add(y);
		switch(unittype){
			case dragon:
				args_int.add(0);
				break;
			case player:
				args_int.add(1);
				break;
		}
		args_int.add(unit_id);
		args_int.add(origin_id);
	}
	
	//constructor with only 2 values of coordinates(for attack, heal)
	protected Command(CommandType type, int sourceX, int sourceY,int  targetX, int targetY, int diffAP, int origin_id){
		this.type = type;
		args_int = new ArrayList<Integer>();
		args_int.add(sourceX);
		args_int.add(sourceY);
		args_int.add(targetX);
		args_int.add(targetY);
		args_int.add(diffAP);
		args_int.add(origin_id);
	}
	
	//constructor with only 2 values of coordinates(for move)
	protected Command(CommandType type, int sourceX, int sourceY,int  targetX, int targetY, int origin_id){
		this.type = type;
		args_int = new ArrayList<Integer>();
		args_int.add(sourceX);
		args_int.add(sourceY);
		args_int.add(targetX);
		args_int.add(targetY);
		args_int.add(origin_id);
	}
	
	//constructor for copy
	protected Command(Command origin){
		this.type = origin.type;
		this.args_int = new ArrayList<Integer>();
		for(int i=0; i < origin.args_int.size(); i++)
			args_int.add(origin.arg(i));
	}
	
	//return command type
	public CommandType getType(){
		return type;
	}
	
	//return sender id
	public int getOriginId(){
		return arg(args_int.size() - 1);
	}
	
	//return integer argument i
	public int arg(int i){
		if (i < args_int.size())
			return args_int.get(i);
		return -1;
	}
	
	//checks if content of another command is equal to its content
	public synchronized boolean sameAs(Command c){
		if(c.type != this.type)
			return false;
		for(int i = 0; i < c.args_int.size(); i++){
			if(c.args_int.get(i) - this.args_int.get(i) != 0){
				return false;
			}
		}
		return true;
	}
	
	//method to process a command 
	//it is necessary to send the Inverse command if processing a normal attack or heal (to take care of new HP < 0 or > maxHP)
	public Unit processCommand(Battlefield battlefield, Machine machine, Command inverse, int timestamp, int step_pos, boolean beenProcessed){
		Unit new_unit = null;
		Unit source = null;
		Unit target = null;
		boolean success = false;
		int x, y, unit_id, diffAP;
				
		switch(this.getType()){
			case spawn:
				UnitType unittype;
				if(this.arg(5)==1)
					unittype = UnitType.player;
				else
					unittype = UnitType.dragon;
				
				x = this.arg(3);
				y = this.arg(4);
				
				//checks if it needs to create a new or uses the received
				if(this.arg(6) == -1 || battlefield.idExist(this.arg(6)))
					unit_id = battlefield.getCurrID();
				else
					unit_id = this.arg(6);
				if(battlefield.posFree(x, y)){
					new_unit = new Unit(this.arg(0),this.arg(1),this.arg(2),x,y,unit_id,unittype,battlefield);
					success = battlefield.spawnUnit(new_unit);
				}
				
				//if is host and failed to spawn, will try to do it again
				if(!success && inverse != null){ 
					if(machine.id == machine.host_id){
						//try to spawn the player
						//find a free spot
						do{
							x = (int)(Math.random()*battlefield.getWidth());
							y = (int)(Math.random()*battlefield.getHeight());
						}while(!battlefield.posFree(x, y));
					
						machine.val_buffer.addCommit(PhaseType.request, new Step(new Command(CommandType.spawn,arg(0),arg(1),arg(2),x,y,this.arg(5) == 0 ? UnitType.dragon : UnitType.player, this.arg(6),this.arg(7)),new Command(CommandType.remove,x,y,this.arg(7)),machine.timestamp),false,machine.host_id);
					}
					return null;
				}
				
				if(machine.id != this.arg(7) && machine.my_unit_id != unit_id)
					new_unit = null;
				else if(machine.id >= machine.serversN && machine.my_unit_id == -1)
					machine.my_unit_id = unit_id;
				
				break;
			case remove:
				source = battlefield.getUnit(this.arg(0), this.arg(1));
				battlefield.removeUnit(source, inverse!=null);
				break;
			case attack:				
				source = battlefield.getUnit(this.arg(0), this.arg(1));
				target = battlefield.getUnit(this.arg(2), this.arg(3));
				if(source != null && source.checkRunning()){
					if(inverse != null)
						diffAP = source.attackUnit(target,0,timestamp,this.getOriginId());
					else
						diffAP = source.attackUnit(target,this.arg(5),timestamp,-1);

					if(inverse != null)
						inverse.args_int.set(5, diffAP);
				}
				break;
			case heal:
				source = battlefield.getUnit(this.arg(0), this.arg(1));
				target = battlefield.getUnit(this.arg(2), this.arg(3));
				if(source != null && source.checkRunning()){
					//means that is reviving (doing an inverse command)
					if(inverse == null){
						diffAP = source.healUnit(target,this.arg(5),timestamp,true);
					}
					else
						diffAP = source.healUnit(target,0,timestamp,false);
						
					if(inverse != null)
						inverse.args_int.set(5, diffAP);				
				}
				break;
			case move:
				source = battlefield.getUnit(this.arg(0),this.arg(1));
				if(source != null && source.checkRunning())
					source.moveUnit(this.arg(2),this.arg(3));
				break;
			case gameStart:
				machine.startGame();
				System.out.println(machine.owner + ": " +"BATTLE BEGINS");
				machine.addOutput("BATTLE BEGINS");
				break;
			case falseStart:
				machine.gameStarted = false;
				machine.pauseUnits();
				System.out.println(machine.owner + ": " +"FALSE START");
				machine.addOutput("FALSE START");
				break;
			default:
				break;
		}
		
		return new_unit;
	}
	
	public synchronized boolean checkValidity(Battlefield bf){
		boolean decision = true;
		
		if(type == CommandType.attack){
			Unit source = bf.getUnit(this.arg(0), this.arg(1));
			Unit target = bf.getUnit(this.arg(2), this.arg(3));
	
			if(source != null && target != null && source.getType() == target.getType())
				decision = false;
		}else if (type == CommandType.heal){
			Unit source = bf.getUnit(this.arg(0), this.arg(1));
			Unit target = bf.getUnit(this.arg(2), this.arg(3));
			if(source != null && target != null && (source.getType() == UnitType.dragon || target.getType() == UnitType.dragon))
				decision = false;
		}else if (type == CommandType.move){
			if(Math.abs(arg(2)) + Math.abs(arg(3)) > 1)
				decision = false;
		}
		
		//if(bf.getMachine().id == 0)
		//	decision = !decision;
		
		return decision;
	}
}