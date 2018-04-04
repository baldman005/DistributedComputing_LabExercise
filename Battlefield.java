import java.io.Serializable;
import java.util.ArrayList;

//class that contains the battlefield
public class Battlefield implements Serializable{
	private static final long serialVersionUID = 1L;
	private int MAP_WIDTH, MAP_HEIGHT;
	public Unit[][] map;
	public ArrayList<Unit> units;
	// ID of next unit that spawns, somehow that gets transmitted
	private int currID;
	//machine(server/client) connected to battlefield 
	private Machine machine;
	public boolean gameOver = false;
	
	//constructor
	protected Battlefield(int width, int height,Machine machine) {		
		map = new Unit[width][height];
		units = new ArrayList<Unit>();
		currID = 0;
		MAP_WIDTH = width;
		MAP_HEIGHT = height;
		this.machine = machine;
	}
	
	//returns map of battlefield
	public ArrayList<Unit> getUnits(){
		ArrayList<Unit> units = new ArrayList<Unit>();
		for (Unit u:this.units) {
			units.add(new Unit(u.getMaxHP(),u.getHP(),u.getAP(),u.getX(),u.getY(),u.getUnitID(), u.getType(), null));
		}
		return units;
		
	}
	
	//changes the map of the battlefield
	public void setUnits(ArrayList<Unit> units, int units_owner_id) {
		units = new ArrayList<Unit>(units);
		map = new Unit[MAP_WIDTH][MAP_HEIGHT];
		for(Unit u:units){
			if (map[u.getX()][u.getY()]==null) {
				machine.addStep(new Command(CommandType.spawn,u.getMaxHP(),u.getHP(),u.getAP(),u.getX(),u.getY(),u.getType(),u.getUnitID(),units_owner_id),new Command(CommandType.remove,u.getX(),u.getY(),units_owner_id),machine.timestamp);
			}
		}
	}
	
	//for battleviewer
	public void setUnits(ArrayList<Unit> units) {
		for (int i =0 ; i < MAP_WIDTH; i++) {
			for (int j= 0; j<MAP_HEIGHT; j++) {
				map[i][j] = null;
			}
		}
		for(Unit u:units) {
			if (map[u.getX()][u.getY()]==null) {
				map[u.getX()][u.getY()] = new Unit(u.getMaxHP(), u.getHP(), u.getAP(), u.getX(), u.getY(), u.getUnitID(), u.getType(), this);
			}
			else {
				map[u.getX()][u.getY()].setUnitID(u.getUnitID());
			}
		} 
	}
	
	//return machine
	public Machine getMachine(){
		return machine;
	}
	
	//check if there is a unit with the id
	public boolean idExist(int id){
		if(units == null) return false;
		for(Unit u:units)
			if(u.getUnitID() == id)
				return true;
		return false;
	}
	
	//spawns a unit in the battlefield
	public synchronized boolean spawnUnit(Unit unit){
		if(map[unit.getX()][unit.getY()] == null){
			System.out.println(machine.owner + ": " + unit.getType() + " "+ unit.getUnitID() + " has SPAWNED in position (" + unit.getX() + "," + unit.getY()+"); HP: "+unit.getHP()+" AP: "+unit.getAP());
			machine.addOutput(unit.getType() + " "+ unit.getUnitID() + " has SPAWNED in position (" + unit.getX() + "," + unit.getY()+"); HP: "+unit.getHP()+" AP: "+unit.getAP());
			
			map[unit.getX()][unit.getY()] = unit;
			units.add(unit);
			//update current id
			if(unit.getUnitID() >=  currID){
				currID = unit.getUnitID()+1;
			}
			
			if(gameOver && !this.VicOrDraw())
				gameOver = false;
			
			return true;
		}
		else
			return false;
	}
	
	//check if a faction won or if there has been a draw. If yes then return true
	public synchronized boolean VicOrDraw(){
		if(units == null)
			return false;
		
		if(units.size() < 2)
			return true;
		for(int i=1; i<units.size(); i++)
			if(units.get(i-1).getType() != units.get(i).getType())
				return false;
		return true;
	}
		
	//removes a unit from the battlefield
	public synchronized void removeUnit(Unit unit ,boolean isNormal){
		if(unit != null && ((unit.getHP() == 0 && isNormal) || !isNormal)){
			machine.my_units.remove(unit);
			map[unit.getX()][unit.getY()] = null;
			units.remove(unit);
			
			System.out.println(machine.owner + ": " + unit.getType() + " "+ unit.getUnitID() + " has been REMOVED");
			machine.addOutput(unit.getType() + " "+ unit.getUnitID() + " has been REMOVED");
		}
		
		if(this.VicOrDraw())
			gameOver = true;
	}
	
	//finds nearest unit of type UnitType in battlefield
	public synchronized Unit getNearestUnitType(Unit source, UnitType type){
		int dist, minDist = 10000; //initial minimum distance found is infinite
		Unit closest = null;
			
		for(Unit u:units){
			if(u.getType()!=type)
				continue;
			
			dist = source.calcDist(u);
			//u is equal to source unit
			if(dist == 0)
				continue;
			else if(dist < minDist){
				minDist = dist;
				closest = u;
			}
		}
		return closest;
	}
	
	//moves unit in the battlefield
	public synchronized void moveUnitBf(Unit unit, int new_x, int new_y){
		map[unit.getX()][unit.getY()] = null;
		map[new_x][new_y] = unit;
	}
	
	//returns MAP_WIDTH
	public int getWidth(){
		return MAP_WIDTH;
	}
	
	//returns MAP_HEIGHT
	public int getHeight(){
		return MAP_HEIGHT;
	}
	
	//returns unit at position (x,y)
	public synchronized Unit getUnit(int x, int y){
		return map[x][y];
	}
	
	//return the currID
	public synchronized int getCurrID(){
		return currID;
	}
	
	//returns the boolean gameOver
	public boolean checkGameOver(){
		if(machine.finalRollPos != -1){
			return false;
		}
		return gameOver;
	}
	
	//returns the number of units
	public synchronized int getUnitsNumber(){
		return units.size();
	}
	
	//checks if position is occupied or not
	public synchronized boolean posFree(int x,int y){
		if(map[x][y] == null)
			return true;
		return false;
	}
	
	//method to print the surviving units
	public void printSurvivingUnits(){
		machine.addOutput("DEBUG SEE STEPS");
		for(int j = 0; j < machine.steps.size(); j++){
			machine.addOutput("machine"  + machine.steps.get(j).getOriginId() + " " + machine.steps.get(j).getNormalCommand().getType() + " "+ machine.steps.get(j).getTimestamp());
		}
		
		if(machine.crash){
			System.out.println(machine.owner + ": " +"MACHINE DIED");
			machine.addOutput("MACHINE DIED");
			machine.addOutput("END");		
			return;
		}
		
		System.out.println(machine.owner + ": " +"GAME OVER");
		machine.addOutput("GAME OVER");
		if(units == null || units.size() == 0){
			System.out.println(machine.owner + ": " +"CANNOT RECONNECT, IT HAS FINISHED ");
			machine.addOutput("CANNOT RECONNECT, IT HAS FINISHED ");
			machine.addOutput("END");
			return;
		}
		else if(VicOrDraw())
			if(units.get(0).getType() == UnitType.dragon){
				System.out.println(machine.owner + ": " +"DRAGONS continue to rule the Silence Fields");
				machine.addOutput("DRAGONS continue to rule the Silence Fields");;
			}
			else{
				System.out.println(machine.owner + ": " +"KNIGHTS have brought justice to the Silence Fields");
				machine.addOutput("KNIGHTS have brought justice to the Silence Fields");
			}
		else{
			System.out.println(machine.owner + ": " +"TIMEOUT");
			machine.addOutput("TIMEOUT");
		}
		System.out.println(machine.owner + ": " +"Surviving units:");
		machine.addOutput("Surviving units:");
		for(Unit u:units){
			System.out.println(machine.owner + ": " +u.getType()+" unit "+u.getUnitID()+"; pos: ("+u.getX()+","+u.getY()+"); HP: "+u.getHP()+"("+u.getRemHP()+"%)");
			machine.addOutput(u.getType()+" unit "+u.getUnitID()+"; pos: ("+u.getX()+","+u.getY()+"); HP: "+u.getHP()+"("+u.getRemHP()+"%)");
		}
		machine.addOutput("END");		
	}
}
