import java.io.Serializable;
import java.util.ArrayList;

public class Unit implements Serializable{
	private static final long serialVersionUID = 1L;

	// Position of the unit
	private int x, y;

	// Health
	private int maxHP;
	private int HP;

	// Attack points
	private int AP;

	// Identifier of the unit
	private int unitID;
	
	// Unit Type
	private UnitType unittype;
	
	// Battlefield in which this unit is located
	private Battlefield bf;

	// Thread that processes the unit
	private Thread t;
	
	//boolean
	public boolean paused = false;
	
	// Constructor
	protected Unit(int maxHP, int HP, int AP, int x, int y, int unitID, UnitType unittype, Battlefield bf){
		this.maxHP = maxHP;
		this.HP = HP;
		this.AP = AP;
		this.x = x; this.y = y;
		this.unitID = unitID;
		this.unittype = unittype;
		this.bf = bf;
	}
	
	//Constructor to copy another unit
	protected Unit(Unit origin){
		this.maxHP = origin.maxHP;
		this.HP = origin.HP;
		this.AP = origin.AP;
		this.x = origin.x; this.y = origin.y;
		this.unitID = origin.unitID;
		this.unittype = origin.unittype;
		this.bf = origin.bf;
	}
	
	//return maxHP
	public int getMaxHP(){
		return this.maxHP;
	}
	
	//return percentage of HP remaining
	public int getRemHP(){
		return ((HP*100)/maxHP);
	}
	
	//returns unit AP
	public int getAP(){
		return this.AP;
	}
		
	
	//return X position
	public int getX(){
		return this.x;
	}
	
	//return Y position
	public int getY(){
		return this.y;
	}
	
	//return unitID
	public int getUnitID(){
		return this.unitID;
	}
	
	//return HP
	public int getHP(){
		return this.HP;
	}
	
	//return unit type
	public UnitType getType(){
		return unittype;
	}
	
	public void setUnitID(int id) {
		this.unitID = id;
	}
	
	//calculate distance from other unit u
	public synchronized int calcDist(Unit u){
		return (Math.abs(u.x - x) + Math.abs(u.y - y));
	}
	
	
	/*update HP
	 returns 1 if Unit still lives, 0 if it dies
	 */
	public synchronized int updateUnitHP(int AP_SourceUnit, int timestamp, int origin_id){
		int diff_AP;
		
		HP = HP + AP_SourceUnit;
		if (maxHP < HP) {
			diff_AP = HP - maxHP;
			HP = maxHP;
		}
		else if (HP <= 0){
			diff_AP = -HP;
			HP = 0;
			bf.getMachine().killed = true;
		}
		else{
			diff_AP = 0;
		}
		System.out.println(bf.getMachine().owner + ": " +"UPDATE_HP: " + this.unittype + " unit "+ this.unitID + " now has " + this.HP + " HP" + "(" + this.getRemHP()+ "%)");
		bf.getMachine().addOutput("UPDATE_HP: " + this.unittype + " unit "+ this.unitID + " now has " + this.HP + " HP" + "(" + this.getRemHP()+ "%)");
		return diff_AP;
	}
	

	//move unit
	public synchronized void moveUnit(int moveX, int moveY){
		int newX, newY;
		newX = this.x + moveX;
		newY = this.y + moveY;
		
		// check boundaries
		if (newX < 0)
			newX = 0;
		else if (newX >= bf.getWidth())
			newX = bf.getWidth()-1;
		if (newY < 0)
			newY = 0;
		else if (newY >= bf.getHeight())
			newY = bf.getHeight()-1;
		
		if(!bf.posFree(newX, newY))
			return;
		
		bf.moveUnitBf(this, newX, newY);
		this.x = newX;
		this.y = newY;	
		System.out.println(bf.getMachine().owner + ": " +this.unittype + " "+ this.unitID + " has MOVED to (" + this.x + "," +this.y +")");
		bf.getMachine().addOutput(this.unittype + " "+ this.unitID + " has MOVED to (" + this.x + "," +this.y +")");
	}
	
	//attack another unit
	public synchronized int attackUnit(Unit target, int diffAP, int timestamp,int origin_id){
		if(target != null && target.checkRunning()){
			System.out.println(bf.getMachine().owner + ": " +this.unittype+" "+this.unitID+" with AP "+this.AP+" ATTACKS "+target.unittype+" "+target.unitID+" with "+target.HP+ " HP (" + target.getRemHP()+ "%)");
			bf.getMachine().addOutput(this.unittype+" "+this.unitID+" with AP "+this.AP+" ATTACKS "+target.unittype+" "+target.unitID+" with "+target.HP+ " HP (" + target.getRemHP()+ "%)");
			return(target.updateUnitHP(-(this.AP-diffAP),timestamp,origin_id));
		}
		return -1;
	}
	
	//heal another unit
	public synchronized int healUnit(Unit target, int diffAP, int timestamp, boolean revivePrevDead){
		if(target != null && (target.checkRunning() || revivePrevDead)){
			System.out.println(bf.getMachine().owner + ": " + this.unittype+" "+this.unitID+" with AP "+this.AP+" HEALS "+target.unittype+" "+target.unitID+" with "+target.HP + " HP (" + target.getRemHP()+ "%)");
			bf.getMachine().addOutput(this.unittype+" "+this.unitID+" with AP "+this.AP+" HEALS "+target.unittype+" "+target.unitID+" with "+target.HP + " HP (" + target.getRemHP()+ "%)");
			return(target.updateUnitHP(this.AP - diffAP,timestamp,-1));
		}
		return -1;
	}
	
	//start thread to process the unit
	public synchronized void startProc(){
		if(paused == true){
			paused = false;
			return;
		}
		
		switch(unittype){
			case dragon:
				t = new Thread(new Dragon(this, bf));
				t.start();
				break;
			case player:
				t = new Thread(new Player(this, bf));
				t.start();
				break;
			default:
				break;
		}
	}
	
	//check if it's running
	public boolean checkRunning(){
		if(this.HP > 0)
			return true;
		return false;
	}
}
