import java.io.Serializable;
import java.sql.Timestamp;

//contains normal command and inverse
public class Step implements Serializable{
	private static final long serialVersionUID = 1L;
	private Command normal = null;
	private Command inverse = null;
	private int timestamp;
	public boolean beenProcessed;
	
	protected Step(Command normal, Command inverse, int timestamp){
		this.normal = normal;
		this.inverse = inverse;
		this.timestamp = timestamp;
		beenProcessed = false;
	}
	
	//constructor to copy
	protected Step(Step origin){
		if(origin == null) return;
		
		if(origin.normal != null)
			this.normal = new Command(origin.normal);
		if(origin.inverse != null)
			this.inverse = new Command(origin.inverse);
		this.timestamp = origin.timestamp;
		beenProcessed = false;
	}
	
	public Command getNormalCommand(){
		return normal;
	}
	
	public Command getInvCommand(){
		return inverse;
	}
	
	public int getTimestamp() {
		return timestamp;
	}
	
	public int getOriginId(){
		return normal.getOriginId();
	}
}
