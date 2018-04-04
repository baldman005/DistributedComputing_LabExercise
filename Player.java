import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class Player implements Runnable {
	//Player unit
	private Unit unit;
	//Battlefield
	private Battlefield bf;
	//Delay between actions
	private final int MIN_actionTs = 300;
	private final int MAX_actionTs = 300;
	//Attack range
	private final int attackDist = 2;
	//Heal range
	private final int healDist = 5;
	
	//Constructor
	public Player(Unit unit, Battlefield bf){
		this.unit = unit;
		this.bf = bf;
	}
	
	//tries to heal a close wounded ally; returns true if success, false otherwise
	private boolean healWoundedAlly() throws RemoteException, MalformedURLException, NotBoundException{
		Unit ally;
		Step s;
		
		Interface dest = (Interface) java.rmi.Naming.lookup(bf.getMachine().owner);
		
		//tries to heal a nearby player
		ally = bf.getNearestUnitType(unit,UnitType.player);
		if(ally != null && ally.checkRunning() && unit.calcDist(ally) <= healDist && ally.getRemHP() <= 50){
			s = new Step(new Command(CommandType.heal,unit.getX(),unit.getY(),ally.getX(),ally.getY(),0,bf.getMachine().id),new Command(CommandType.attack,unit.getX(),unit.getY(),ally.getX(),ally.getY(),0,bf.getMachine().id),bf.getMachine().timestamp); 
			bf.getMachine().val_buffer.addCommit(PhaseType.mine, s, false,bf.getMachine().host_id);
			dest.sendMessage(PhaseType.request,s,false,bf.getMachine().host_id,false);
			return true;
		}
		return false;
	}
		
	//moves to a dragon
	//if there is an obstacle in the way just stays in the same place
	private synchronized void moveToDragon(Unit dragon) throws MalformedURLException, RemoteException, NotBoundException{
		int disX, disY, vX, vY;
		Interface dest = (Interface) java.rmi.Naming.lookup(bf.getMachine().owner);
		Step s;
		
		disX = dragon.getX() - unit.getX();
		disY = dragon.getY() - unit.getY();
		vX = Integer.signum(disX);
		vY = Integer.signum(disY);
		
		//tries to move
		if(Math.abs(disX) == Math.abs(disY)){
			if(bf.posFree(unit.getX(), unit.getY()+vY)){
				s = new Step(new Command(CommandType.move,unit.getX(),unit.getY(),0,vY,1,bf.getMachine().id),new Command(CommandType.move,unit.getX(),unit.getY()+vY,0,-vY,0,bf.getMachine().id),bf.getMachine().timestamp);
				bf.getMachine().val_buffer.addCommit(PhaseType.mine, s, false,bf.getMachine().host_id);
				dest.sendMessage(PhaseType.request,s,false,bf.getMachine().host_id,false);
			}
			else{
				s = new Step(new Command(CommandType.move,unit.getX(),unit.getY(),vX,0,1,bf.getMachine().id),new Command(CommandType.move,unit.getX()+vX,unit.getY(),-vX,0,0,bf.getMachine().id),bf.getMachine().timestamp);
				bf.getMachine().val_buffer.addCommit(PhaseType.mine, s, false,bf.getMachine().host_id);
				dest.sendMessage(PhaseType.request,s,false,bf.getMachine().host_id,false);
			}
		}
		else if(Math.abs(disX) > Math.abs(disY)){
			s = new Step(new Command(CommandType.move,unit.getX(),unit.getY(),vX,0,1,bf.getMachine().id),new Command(CommandType.move,unit.getX()+vX,unit.getY(),-vX,0,0,bf.getMachine().id),bf.getMachine().timestamp);
			bf.getMachine().val_buffer.addCommit(PhaseType.mine, s, false,bf.getMachine().host_id);
			dest.sendMessage(PhaseType.request,s,false,bf.getMachine().host_id,false);
		}
		else{
			s = new Step(new Command(CommandType.move,unit.getX(),unit.getY(),0,vY,1,bf.getMachine().id),new Command(CommandType.move,unit.getX(),unit.getY()+vY,0,-vY,0,bf.getMachine().id),bf.getMachine().timestamp);
			bf.getMachine().val_buffer.addCommit(PhaseType.mine, s, false,bf.getMachine().host_id);
			dest.sendMessage(PhaseType.request,s,false,bf.getMachine().host_id,false);
		}
	}
		
	//tries to attack a close dragon; if not possible, moves to the closest one
	private void attackOrMoveToDragon() throws MalformedURLException, RemoteException, NotBoundException{
		Unit dragon;
		Interface dest = (Interface) java.rmi.Naming.lookup(bf.getMachine().owner);
		
		dragon = bf.getNearestUnitType(unit,UnitType.dragon);
		if(dragon != null){
			
			if(unit.calcDist(dragon) > attackDist){
				moveToDragon(dragon);
			}else{
				Step s = new Step(new Command(CommandType.attack,unit.getX(),unit.getY(),dragon.getX(),dragon.getY(),0,bf.getMachine().id),new Command(CommandType.heal,unit.getX(),unit.getY(),dragon.getX(),dragon.getY(),0,bf.getMachine().id),bf.getMachine().timestamp);
				bf.getMachine().val_buffer.addCommit(PhaseType.mine, s, false,bf.getMachine().host_id);
				dest.sendMessage(PhaseType.request,s,false,bf.getMachine().host_id,false);
			}
		}
	}
		
	//runnable
	@Override
	public void run() {				
		try{
			while(bf != null && unit.checkRunning() && !bf.getMachine().crash && !bf.checkGameOver()){
				if(!unit.paused && !bf.checkGameOver() && !bf.getMachine().changing_host && !bf.getMachine().crash)
					try {
						if(!healWoundedAlly())
							//attacks or moves to a dragon
							attackOrMoveToDragon();
					} catch (RemoteException | MalformedURLException
							| NotBoundException e) {
						e.printStackTrace();
					}
				Thread.sleep((int)(Math.random() * (MAX_actionTs - MIN_actionTs) + MIN_actionTs));
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}