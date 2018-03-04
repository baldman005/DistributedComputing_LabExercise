import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

public class Main {
	private static ArrayList<String> URLs = new ArrayList<String>();
	
	
	public static void main(String args[]) throws FileNotFoundException, UnsupportedEncodingException   {
		/*String rmi_name_0 = "rmi://145.94.187.205:1099/p1";
		String rmi_name_1 = "rmi://145.94.227.158:1100/p2";
		String rmi_name_2 = "rmi://145.94.187.205:1101/p3";*/
		
		//strings with the URLs of the processes
		String rmi_name_0 = "rmi://localhost:1099/p1";
		String rmi_name_1 = "rmi://localhost:1100/p2";
		String rmi_name_2 = "rmi://localhost:1101/p3";
		
		//strings with the filenames for each process
		String file0 = "p0.deliv";
		String file1 = "p1.deliv";
		String file2 = "p2.deliv";
		
		//adds the URLs to the list
		URLs.add(rmi_name_0);
		URLs.add(rmi_name_1);
		URLs.add(rmi_name_2);
		
		//the number of processes is equal to the size of URLs
		int num_procs = URLs.size();
		
		Thread t0;
		Thread t1;
		Thread t2;
		
		try {	
		
		//creates the processes
		Process p0 = new Process(0, num_procs,URLs,file0);
	    Process p1 = new Process(1, num_procs,URLs,file1);
		Process p2 = new Process(2, num_procs,URLs,file2);
		
		//creates the registries
		Registry reg0 = java.rmi.registry.LocateRegistry.createRegistry(1099);
		Registry reg1 = java.rmi.registry.LocateRegistry.createRegistry(1100);
		Registry reg2 = java.rmi.registry.LocateRegistry.createRegistry(1101);
		
		//ATTENTION
		//binds a remote reference to the specified name in the registries
		reg0.bind(rmi_name_0, p0);
		reg1.bind(rmi_name_1, p1);
		reg2.bind(rmi_name_2, p2);
		
		//bins the URL to the processes
		java.rmi.Naming.bind(rmi_name_0, p0);
	    java.rmi.Naming.bind(rmi_name_1, p1);
	    java.rmi.Naming.bind(rmi_name_2, p2);
	    
	    //creates the threads and starts them
	    t0 = new Thread(p0);
	    t0.start();
	    t1 = new Thread(p1);
	    t1.start();
	    t2 = new Thread(p2);
	    t2.start();
	    
	    //waits for every thread to die
	    while(t0.isAlive() || t1.isAlive() || t2.isAlive()) {
	    	Thread.sleep(500);
	    }
	    
	    //sleeps in the end for 2s to ensure that, if there is a process
	    //in another host, its thread have died
	    Thread.sleep(2000);
	    
	    //unbinds the registries references
		reg0.unbind(rmi_name_0);
		reg1.unbind(rmi_name_1);
		reg2.unbind(rmi_name_2);
		//unexports the registries
		UnicastRemoteObject.unexportObject(reg0,true);
		UnicastRemoteObject.unexportObject(reg1,true);
		UnicastRemoteObject.unexportObject(reg2,true);

		} catch ( RemoteException| MalformedURLException| 
				AlreadyBoundException | NotBoundException  
				| InterruptedException e) {
			e.printStackTrace();
		}
		//exits to kill the rmi ports
		System.exit(0);		
	}

}
