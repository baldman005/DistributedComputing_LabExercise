//Class that has runnable that processes Dragon Unit
public class Dragon implements Runnable{
	//Dragon unit
	private Unit unit;
	//Battlefield
	private Battlefield bf;
	//Delay between actions
	private final int MIN_actionTs = 300;
	private final int MAX_actionTs = 300;
	//Attack range
	private final int attackDist = 2;
	
	//Constructor
	public Dragon(Unit unit, Battlefield bf){
		this.unit = unit;
		this.bf = bf;
	}
	
	//tries to attack a close player
	private void attackPlayer(){
		Unit player;
		
		player = bf.getNearestUnitType(unit, UnitType.player);
		if(player != null && unit.calcDist(player) <= attackDist){
			bf.getMachine().val_buffer.addCommit(PhaseType.request, new Step(new Command(CommandType.attack,unit.getX(),unit.getY(),player.getX(),player.getY(),0,bf.getMachine().id),new Command(CommandType.heal,unit.getX(),unit.getY(),player.getX(),player.getY(),0,bf.getMachine().id),bf.getMachine().timestamp),false,bf.getMachine().host_id);
		}
	}
	
	//runnable
	@Override
	public void run() {	
		try{
		
		while(bf != null && !bf.getMachine().crash && !bf.checkGameOver() && unit.checkRunning()){
			if(!unit.paused && !bf.checkGameOver() && !bf.getMachine().changing_host && !bf.getMachine().crash)
				attackPlayer();
			Thread.sleep((int)(Math.random() * (MAX_actionTs - MIN_actionTs) + MIN_actionTs));
		}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

