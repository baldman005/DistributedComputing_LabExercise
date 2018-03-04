import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

public class Process extends UnicastRemoteObject implements Reception, Runnable {
	private int ts = 0;													//timestamp of the process, that starts as 0
	private int id;														//id of the process
	private static int num_procs;										//total number of processes in the system
	private ArrayList<Message> buffer = new ArrayList<Message>();		//buffer with the messages received but not delivered
	private ArrayList<Message> deliveries = new ArrayList<Message>();	//list with the messages delivered
	private ArrayList<Ack> acks = new ArrayList<Ack>();					//list with the acks received by the process
	private static ArrayList<String> URLs = new ArrayList<String>();	//list of the URLs of all processes in the system
	private int state = 0;												//current state of the process (details ahead in the runnable)
	private boolean check = true;										//boolean to check if process can stop working
	private PrintWriter writer;											//writer associated with the file to which the process writes 
	
	//constructor of Process
	protected Process(int id, int num_procs, ArrayList<String> URLs, String filename) throws RemoteException, FileNotFoundException, UnsupportedEncodingException {
		super(1099); //AQUI 1099?
		this.id=id;
		Process.num_procs=num_procs;
		Process.URLs=URLs;
		this.writer = new PrintWriter(filename, "UTF-8");;
	}
	
	//prints the buffer to file and System.out
	public void print_and_write_buffer(){
		System.out.println("Buffer " + this.id);
		this.writer.println("Buffer " + this.id);	
		for(Message e:this.buffer){
			e.print_message();
			e.write_message(this.writer);
		}
	}
	
	//prints the deliveries to file and System.out
	public void print_and_write_deliv(){
		System.out.println("Deliveries " + this.id);
		this.writer.println("Deliveries " + this.id);	
		for(Message e:this.deliveries){
			e.print_message();
			e.write_message(this.writer);
		}
	}
	
	//prints the acks to file and System.out
	public void print_and_write_ack(){
		System.out.println("Acks " + this.id);
		this.writer.println("Acks " + this.id);	
		for(Ack e:this.acks){
			e.print_ack();
			e.write_ack(this.writer);
		}
	}
	
	//prints all the lists, id and ts of the process
	public void print_content(){
		System.out.println("------------------");
		System.out.println("Process:" + this.id + " ts:" + this.ts);
		this.writer.println("Process:" + this.id + " ts:" + this.ts);	
		this.print_and_write_buffer();
		this.print_and_write_ack();
		this.print_and_write_deliv();
		System.out.println("------------------");
	}
	
	/*
	 * Delivers the message in the head of the buffer
	 */
	public void deliver (int pos) {
		Message m;
		
		//m is the head
		m = buffer.get(0);
		
		//adds it to the deliveries list and removes it from buffer
		deliveries.add(m);
		buffer.remove(0);
		
		//removes the ack associated with m from the acks list
		acks.remove(pos);
		
		//increments the timestamp
		this.ts++;
		
		return;		
	}

	/*
	 * Process receives a Message m
	 */
	@Override
	public  void receive_message(Message m) throws RemoteException, InterruptedException, MalformedURLException, NotBoundException {
		synchronized (this){
		//flag check changes to false
		this.check = false;
		
		//add the message to buffer and sorts it
		buffer.add(m);
		Collections.sort(this.buffer, new SortQueue());
		}
		
		//broadcasts an ack
		Ack ack = new Ack(m.ts,m.id);
		/*int delay = ThreadLocalRandom.current().nextInt(200, 500);
		Thread.sleep(delay);*/
		this.broadcast(null, ack);
		
		//flag check changes to true
		this.check = true;
				
		return;
	}

	/*
	 * Process receives an Ack a
	 */
	@Override
	public  void receive_ack(Ack a) throws RemoteException {
		synchronized (this) {
		//calls method that manages the ack
		this.manage_ack(a);
		}
		return;

	}
	
	/*
	 * Process broadcasts Message m or Ack a
	 */
	public void broadcast(Message m, Ack a) throws InterruptedException, MalformedURLException, RemoteException, NotBoundException{
		//check if inputs are null
		if (a == null && m == null) {
			return;
		}
		
		//if message is null the process broadcasts the ack
		int i;
		if(m == null) {
			for(i = 0; i<num_procs; i++) {
				this.send_Ack(a,i);
			}
		}
		//if message isn't null the process broadcast it
		else {
			for(i = 0; i<num_procs; i++) {
				this.send_message(m,i);
			}
		}
		
		return;
	}
	
	/*
	 * Process sends Message m to process with id equal to proc_id
	 */
	public void send_message(Message m, int proc_id) throws MalformedURLException, RemoteException, NotBoundException, InterruptedException{
		//to simulate the delay starts a Delay_Message_Thread
		Delay_Message_Thread thread = new Delay_Message_Thread( m, URLs.get(proc_id));
		new Thread(thread).start();

		return;
	}
	
	/*
	 * Process sends Ack ack to process with id equal to proc_id
	 */
	public void send_Ack(Ack ack, int proc_id) throws InterruptedException{
		//to simulate the delay starts a Delay_Ack_Thread
		Delay_Ack_Thread thread = new Delay_Ack_Thread( ack, URLs.get(proc_id));
		new Thread(thread).start();
		return;
	}
	
	/*
	 * Checks if the ack already exists in the acks list.
	 * If it does the method simply increments the counter of the ack in list.
	 * If not adds it to the ack list.
	 */
	private void manage_ack(Ack ack){
		
		int i;
		boolean exist = false;
		Ack e;
		
		//check if it already received the ack
		for(i=0;i<this.acks.size();i++){
			e = this.acks.get(i);
			//checks if it found the ack
			if(ack.id == e.id && ack.ts == e.ts){
				//increments the counter, and changes the flag exist to true
				e.counter++;
				exist = true;
				break;
			}
		}
		
		//if the ack is new add its
		if(!exist)
			this.acks.add(ack);
	}

	//runnable
	@Override
	public void run() {
		int m;
		Message head;
		Ack a;
		boolean termination = false;
		
		/*
		 * the runnable between the processes has differences
		 */
		if(this.id == 0) {
			try {
				//process 0 broadcasts a message 5 in the beggining
				this.broadcast(new Message(5,this.ts,this.id),null);
				while(!termination) {
					//simulation of the internal clock
					Thread.sleep(100);
					
					//checks if it can deliver the head of buffer
					m = -1;
					if(this.buffer.size()>0){
						head = this.buffer.get(0);
						/*find if there is an ack with counter equal to num_procs,
						 *associated with the head of the buffer
						 */
						for(int i=0; i<this.acks.size(); i++){
							a = this.acks.get(i);
							//if it finds one delivers the head
							if(a.ts==head.ts && a.id==head.id && a.counter==num_procs){
								m = head.m;
								this.deliver(i);
							}
						}
					}
					
					//state machine to demonstrate the algorithm
					switch(state){
						case(0): 
							/*
							 * if it is in the initial stage, and receives the first message
							 * broadcasted(with m equal to 5), broadcast a new one
							 * and changes to state 1
							 */
							if (m == 5){
								this.broadcast(new Message(0,this.ts,this.id),null);
								state = 1;
							}
							break;
						case(1):
							/*
							 * if it is in the final stage, and all messages have been 
							 * delivered (in this example there are 4 messages), prints 
							 * the content of the process and flag terminate changes to true
							 */
							if(this.deliveries.size() == 4 && this.check == true){
								System.out.println(this.id + " is finished");
								this.print_content();
								this.writer.close();
								termination = true;
							}
							break;
					}
				}
			} catch (MalformedURLException | RemoteException | NotBoundException | InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		if(this.id == 1) {
			try {
				while(!termination) {
					//simulation of the internal clock
					Thread.sleep(100);
					
					//checks if it can deliver the head of buffer
					m = -1;
					if(this.buffer.size()>0){
						head = this.buffer.get(0);
						/*find if there is an ack with counter equal to num_procs,
						 *associated with the head of the buffer
						 */
						for(int i=0; i<this.acks.size(); i++){
							a = this.acks.get(i);
							//if it finds one delivers the head
							if(a.ts==head.ts && a.id==head.id && a.counter==num_procs){
								m = head.m;
								this.deliver(i);
							}
						}
					}
					
					//state machine to demonstrate the algorithm
					switch(state){
						case(0): 
							/*
							 * if it is in the initial stage, and receives the first message
							 * broadcasted(with m equal to 5), broadcast a new one
							 * and changes to state 1
							 */
							if (m == 5){
								this.broadcast(new Message(1,this.ts,this.id),null);
								state = 1;
							}
							break;
						case(1):
							/*
							 * if it is in the final stage, and all messages have been 
							 * delivered (in this example there are 4 messages), prints 
							 * the content of the process and flag terminate changes to true
							 */
							if(this.deliveries.size() == 4 && this.check == true){
								System.out.println(this.id + " is finished");
								this.print_content();
								this.writer.close();
								termination = true;
							}
							break;
					}
				}
			} catch (MalformedURLException | RemoteException | NotBoundException | InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		if(this.id == 2) {
			try {
				while(!termination) {
					//simulation of the internal clock
					Thread.sleep(100);
					
					//checks if it can deliver the head of buffer
					m = -1;
					if(this.buffer.size()>0){
						head = this.buffer.get(0);
						/*find if there is an ack with counter equal to num_procs,
						 *associated with the head of the buffer
						 */
						for(int i=0; i<this.acks.size(); i++){
							a = this.acks.get(i);
							//if it finds one delivers the head
							if(a.ts==head.ts && a.id==head.id && a.counter==num_procs){
								m = head.m;
								this.deliver(i);
							}
						}
					}
					
					//state machine to demonstrate the algorithm
					switch(state){
						case(0): 
							/*
							 * if it is in the initial stage, and receives the first message
							 * broadcasted(with m equal to 5), broadcast a new one
							 * and changes to state 1
							 */
							if (m == 5){
								this.broadcast(new Message(2,this.ts,this.id),null);
								state = 1;
							}
							break;
						case(1):
							/*
							 * if it is in the final stage, and all messages have been 
							 * delivered (in this example there are 4 messages), prints 
							 * the content of the process and flag terminate changes to true
							 */
							if(this.deliveries.size() == 4 && this.check == true){
								System.out.println(this.id + " is finished");
								this.print_content();
								this.writer.close();
								termination = true;
							}
							break;
					}
				}
			} catch (MalformedURLException | RemoteException | NotBoundException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
