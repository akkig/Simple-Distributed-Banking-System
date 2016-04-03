package utd.aos.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Semaphore;

import utd.aos.utils.Message;
import utd.aos.utils.MessageRecord;
import utd.aos.utils.MutexMessage;
import utd.aos.utils.MutexMessage.MessageType;
import utd.aos.utils.Operations;
import utd.aos.utils.Resource;
import utd.aos.utils.SocketMap;
import utd.aos.utils.Operations.OperationMethod;
import utd.aos.utils.Operations.OperationType;
import utd.aos.utils.Request;
import utd.aos.utils.RequestComparator;

public class Client implements Runnable{
	
	public static int id;
	public static Map<String, SocketMap> quorum;
	public static Map<String, SocketMap> allClientsSockets;
	public static Map<Integer, InetSocketAddress> otherClients;
	public static Map<String, Integer> hostIdMap;	
	public static Map<String, SocketMap> allClientsListenerSockets;
	
	public static SocketMap serverSocketMap;
	
	public static InetAddress ip;
	public static Integer port;
	
	public static Semaphore mutex = new Semaphore(1);	
	
	//public static Semaphore gotallReplies = new Semaphore(1);
	//public static Semaphore gotallReleases = new Semaphore(1);
	
	public static int pendingReleaseToReceive;
	public static int gotFailed;
	public static int sentYield;
	public static int sentEnquire;
	
	public static long clock;
	
	public static boolean inprocess = false;
	
	public static Map<Integer, Boolean> pendingRepliesToReceive = new HashMap<Integer, Boolean>();
	public static Map<Integer, Boolean> sentYieldMessageTo = new HashMap<Integer, Boolean>();
	
	public static Queue<Integer> request_fifo = new LinkedList<Integer>(); 
	public static Queue<Integer> fail_fifo = new LinkedList<Integer>(); 

	public static int count = 1;
	public static MessageRecord record = new MessageRecord();
	
	public static PriorityQueue<Request> request_q;
	
	public Client() {
	
	}

	//MutexAlgorithm algo;
	
	//blocked: either me executing critical section or didn't get the release from last reply.
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		init();
		Random rand = new Random();
		
		while(count <= 40) {

			Integer delay = rand.nextInt(40);
			
			delay += 10;
			
			reset();

			try {
				
				Thread.sleep(delay);

				Resource resource = new Resource();
				resource.setFilename("test");

				Operations operation = new Operations();
				operation.setOperation(OperationMethod.WRITE);
				operation.setType(OperationType.PERFORM);
				operation.setInputResource(resource);
				operation.setArg(id+" : "+count+" : "+InetAddress.getLocalHost().getHostName()+"\n");

				
				System.out.println("--adding my request to fifo--");
				
				/*
				request_fifo.add(id);
				
				//int size = request_fifo.size();
				while(!request_fifo.isEmpty()) {

					
					int top = request_fifo.remove();
					if(top == id) {
						inprocess = true;
						new Thread(new ClientMainThread(operation)).start();//).start();

					} else {
						//System.out.println("--WAIT allreply sema (other request)--");
						//gotallReplies.acquire();
						serveOthersRequest(top);

						//System.out.println("--RELEASE allreply sema (other request)--");
						//gotallReplies.release();
					}

					
					while(!request_fifo.isEmpty() || inprocess == true) {

						if (!request_fifo.isEmpty()) {
							serveOthersRequest(request_fifo.remove());
						}
						
						Thread.sleep(20);
					}
					
					
					//size--;
				}				
				*/
				Request request = new Request(id, null, MessageType.REQUEST);
				request_q.add(request);
				while(!request_q.isEmpty()) {
					
					Request req = request_q.remove();
					
					if(req.getType().equals(MessageType.REQUEST)) {
						handleRequest(req, operation);
					}
					if(req.getType().equals(MessageType.REPLY)) {
						handleReply(req);
					}
					if(req.getType().equals(MessageType.RELEASE)) {
						handleRelease(req);
					}
					if(req.getType().equals(MessageType.FAILED)) {
						handleFail(req);
					}
					if(req.getType().equals(MessageType.ENQUIRE)) {
						handleEnquire(req);
					}
					if(req.getType().equals(MessageType.YIELD)) {
						handleYield(req);
					}
					if(req.getType().equals(MessageType.GRANT)) {
						handleGrant(req);
					}
				}
				
				while(inprocess) {
					//wait
					//Thread.sleep(50);
					break;
				}
				
				if(!inprocess){
					break;
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				try{
					shutdown();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
			
			printreport();
			count++;

		}
		
		try {
			shutdown();			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void handleRequest(Request req, Operations operation) throws IOException, InterruptedException, ClassNotFoundException {		
		System.out.println("----- "+req.getId()+" ------");
		if(req.getId() == id) {
			
			inprocess = true;
			new Thread(new ClientMainThread(operation)).start();

		} else {
			
			
			SocketMap socketmap = req.getSocketmap();
			String socketHostname = socketmap.getAddr().getHostName();

			int client_id = req.getId();
			record.request++;
			System.out.println("--RECV REQUEST "+socketHostname+"--");
				
				//clock = Math.max(message.getClock(), clock) + 1;
				MutexMessage return_message = new MutexMessage();
				
				if(pendingReleaseToReceive == 0 ) {

					pendingReleaseToReceive = client_id;

					return_message.setId(id);
					return_message.setType(MessageType.REPLY);

					System.out.println("--SENT REPLY to "+socketHostname+"--");

					socketmap.getO_out().writeObject(return_message);

					record.reply++;

				} else {

					//lower id = high priority
					if(pendingReleaseToReceive < client_id) {

						return_message.setId(id);
						return_message.setType(MessageType.FAILED);

						System.out.println("--SENT FAILED "+socketHostname+"--");

						socketmap.getO_out().writeObject(return_message);

						record.fail++;

					} else {

						if(pendingReleaseToReceive != id) {

							return_message.setId(id);
							return_message.setType(MessageType.ENQUIRE);


							InetSocketAddress addr = otherClients.get(pendingReleaseToReceive);
							String client_hostname = addr.getHostName();
							SocketMap client_socket_map = allClientsSockets.get(client_hostname);

							System.out.println("--SENT ENQUIRE  "+client_hostname+"--");

							client_socket_map.getO_out().writeObject(return_message);

							record.enquire++;

							sentEnquire = 1;

						} else {

							request_q.add(req);
						}

					}
				} 
		}
	}
	
	public void handleReply(Request req) {
		SocketMap socketmap = req.getSocketmap();
		String socketHostname = socketmap.getAddr().getHostName();
		int client_id = req.getId();
		
		System.out.println("--RECV REPLY "+socketHostname+"--");
		
		pendingRepliesToReceive.remove(client_id);

		record.reply++;
	}
	
	public void handleRelease(Request req) {
		
		SocketMap socketmap = req.getSocketmap();
		String socketHostname = socketmap.getAddr().getHostName();

		System.out.println("---RECV RELEASE  "+ socketHostname+" --");

		pendingReleaseToReceive = 0;

		System.out.println("--Releasing release sema(release)-");

		record.release++;

	}
	public void handleEnquire(Request req) throws IOException {

		SocketMap socketmap = req.getSocketmap();
		String socketHostname = socketmap.getAddr().getHostName();
		int client_id = req.getId();
		record.enquire++;
		System.out.println("--RECV ENQUIRE "+socketHostname);
		MutexMessage return_message = new MutexMessage();
		
		if(pendingReleaseToReceive == 0) {
			
			pendingReleaseToReceive = client_id;

			return_message.setId(id);
			return_message.setType(MessageType.REPLY);
			
			System.out.println("--SENT REPLY "+socketHostname+"--");

			socketmap.getO_out().writeObject(return_message);
			
			record.reply++;
			
		} else if(gotFailed == 1) {

			return_message.setId(id);
			return_message.setType(MessageType.YIELD);

			sentYieldMessageTo.put(client_id, true);

			sentYield = 1;
			System.out.println("--SENT YIELD "+socketHostname+"--");

			socketmap.getO_out().writeObject(return_message);

			record.yield++;


		} else if (sentYield == 1) {

			return_message.setId(id);
			return_message.setType(MessageType.YIELD);

			sentYieldMessageTo.put(client_id, true);
			sentYield = 1;

			System.out.println("--SENT YIELD "+socketHostname+"--");

			socketmap.getO_out().writeObject(return_message);

			record.yield++;

		} else {
			
			request_q.add(req);
			
		} 

	}
	
	public void handleFail(Request req) {
		SocketMap socketmap = req.getSocketmap();
		String socketHostname = socketmap.getAddr().getHostName();
		
		System.out.println("--RECV FAILED "+socketHostname+"-");
		gotFailed = 1;
		
		record.fail++;
	}
	public void handleYield(Request req) throws IOException {
		
		SocketMap socketmap = req.getSocketmap();
		String socketHostname = socketmap.getAddr().getHostName();

		record.yield++;
		System.out.println("--RECV YIELD "+socketHostname);
		
		request_q.add(req);
		
		if(pendingReleaseToReceive == 0) {
			Request top = request_q.remove();
			
			pendingReleaseToReceive = top.getId();
			sentEnquire = 0;

			InetSocketAddress addr = otherClients.get(top.getId());

			MutexMessage return_message = new MutexMessage();

			String client_hostname = addr.getHostName();
			SocketMap client_socket_map = allClientsSockets.get(client_hostname);

			return_message.setId(id);
			return_message.setType(MessageType.GRANT);

			System.out.println("--SENT GRANT "+client_hostname+"--");

			client_socket_map.getO_out().writeObject(return_message);

			record.grant++;
		}
		
	}
	
	public void handleGrant(Request req) {
		SocketMap socketmap = req.getSocketmap();
		String socketHostname = socketmap.getAddr().getHostName();
		int client_id = req.getId();
		
		System.out.println("--RECV GRANT "+socketHostname+"--");
		
		if(sentYieldMessageTo.containsKey(client_id)) {
			sentYieldMessageTo.remove(client_id);
			if(sentYieldMessageTo.size() == 0) {
				sentYield = 0;
			}
		}
		
		if(pendingRepliesToReceive.containsKey(client_id)) {
			pendingRepliesToReceive.remove(client_id);
		}
		
		if(pendingReleaseToReceive == client_id)  {
			pendingReleaseToReceive = 0;
		}
		record.grant++;
	}
	
	
	public void printreport(){
		File file = new File("record_"+id);
		try {
			if(!file.exists()) {
				file.createNewFile();
			}
			FileWriter fw = new FileWriter(file);
			
			int total_message = record.request + record.reply + record.release + record.fail + record.enquire + record.yield + record.grant;
			
			String report = "For Request: "+count+"\n";
			report += "TIME: "+record.time+" milis\n";
			report += "TOTALMESSAGES:"+total_message+"\n";
			report += "REQUEST: "+record.request+"\n";
			report += "REPLY: "+record.reply+"\n";
			report += "RELEASE: "+record.release+"\n";
			report += "FAIL: "+record.fail+"\n";
			report += "ENQUIRE: "+record.enquire+"\n";
			report += "YIELD: "+record.yield+"\n";
			report += "GRANT: "+record.grant+"\n";
			
			report += "---------------\n";
			
			fw.write(report);
			fw.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void serveOthersRequest(int client_id) throws InterruptedException, IOException {
		InetSocketAddress host = otherClients.get(client_id);
		String socketHostname = host.getHostName();
		
		SocketMap socketMap = allClientsListenerSockets.get(socketHostname);
		MutexMessage return_message = new MutexMessage();

		if(pendingReleaseToReceive == 0 ) {
						

			pendingReleaseToReceive = client_id;

			return_message.setId(id);
			return_message.setType(MessageType.REPLY);

			System.out.println("-----SENT REPLY to "+socketHostname+"--");

			socketMap.getO_out().writeObject(return_message);
			
			record.reply++;


		} else {
			
			//lower id = high priority
			if(pendingReleaseToReceive < client_id) {

				return_message.setId(id);
				return_message.setType(MessageType.FAILED);

				System.out.println("-----SENT FAILED "+socketHostname+"--");

				socketMap.getO_out().writeObject(return_message);
				
				record.fail++;
				
			} else {

				if(pendingReleaseToReceive != id) {

					return_message.setId(id);
					return_message.setType(MessageType.ENQUIRE);

					System.out.println("-----SENT ENQUIRE  "+socketHostname+"--");

					InetSocketAddress addr = otherClients.get(pendingReleaseToReceive);
					String client_hostname = addr.getHostName();
					SocketMap client_socket_map = allClientsSockets.get(client_hostname);

					client_socket_map.getO_out().writeObject(return_message);
					
					record.enquire++;
					
				} else {
					request_fifo.add(client_id);
				}
			}
		}
	}
	
	public void init() {
		
		//Check for all clients to be started
		for(Map.Entry<Integer, InetSocketAddress> entry: otherClients.entrySet()) {
			InetSocketAddress addr = entry.getValue();
			while(true) {
			    try {	    	
					Socket socket = new Socket(addr.getHostName(), addr.getPort());
					InputStream in = socket.getInputStream();
					OutputStream out = socket.getOutputStream();

					ObjectInputStream o_in = new ObjectInputStream(in);
					ObjectOutputStream o_out = new ObjectOutputStream(out);
					
					System.out.println("--Saving streams--");
					
					MutexMessage testmessage = new MutexMessage();
					testmessage.setId(id);
					testmessage.setType(MessageType.TEST);
					o_out.writeObject(testmessage);

					SocketMap socketmap = new SocketMap(socket, o_out, o_in, addr);
					
					if(quorum.containsKey(addr.getHostName())) {

						quorum.put(addr.getHostName(), socketmap);
					}
					if(allClientsSockets == null) {
						allClientsSockets = new HashMap<String, SocketMap>();
						allClientsSockets.put(addr.getHostName(), socketmap);
					} else {
						allClientsSockets.put(addr.getHostName(), socketmap);
					}
					
					new Thread(new ClientsServerThreadListener(socketmap)).start();
					
					System.out.println("Connect success: "+ip.getHostName()+"->"+addr.getHostName());

					if(request_q == null) {
						request_q = new PriorityQueue<Request>(30, new RequestComparator());
					}
					
					break;
			    
			    } catch(ConnectException e) {
			    	System.out.println("Connect failed, waiting and trying again: "+ip.getHostName()+"->"+addr.getHostName());
			        try {
			            Thread.sleep(1000);
			        }
			        catch(InterruptedException ie) {
			            ie.printStackTrace();
			        }
			    } catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} 
			}
		}

	}
	
	public void shutdown() throws IOException, ClassNotFoundException {
		Operations operation = new Operations();
		operation.setOperation(OperationMethod.TERMINATE);
		serverSocketMap.getO_out().writeObject(operation);
		serverSocketMap.getO_in().readObject();
		
		if(!allClientsListenerSockets.isEmpty()) {
			for (Map.Entry<String, SocketMap> entry : allClientsListenerSockets.entrySet()) {
				if(!entry.getValue().getSocket().isClosed()) {
					entry.getValue().getO_out().close();
				}
			}
		}
		
		if(!allClientsSockets.isEmpty()) {
			for (Map.Entry<String, SocketMap> entry : allClientsSockets.entrySet()) {
				if(!entry.getValue().getSocket().isClosed()) {
					entry.getValue().getO_out().close();
				}
			}
		}
	}
	
	public void reset() {
		
		record.enquire = 0;
		record.fail = 0;
		record.release = 0;
		record.reply = 0;
		record.request = 0;
		record.yield = 0;
		record.grant = 0;
		record.time = 0;
		
		/*
		pendingReleaseToReceive = 0;
		gotFailed = 0;
		sentYield = 0;
		
		pendingRepliesToReceive = new HashMap<Integer, Boolean>();
		gotFailedMessageFrom = new HashMap<Integer, Boolean>();
		sentYieldMessageTo = new HashMap<Integer, Boolean>();
		*/
		
	}
	
	public boolean getMutex() throws InterruptedException, IOException {
		
		
		//System.out.println("--WAIT allreply sema (mutex)--");
		//gotallReplies.acquire();
		
		for(Map.Entry<String, SocketMap> entry: quorum.entrySet()) {
			SocketMap quorum_client = entry.getValue();
			String hostname = quorum_client.getAddr().getHostName();
			Integer client_id = hostIdMap.get(hostname);
			MutexMessage message = new MutexMessage(id, MessageType.REQUEST);
			
			System.out.println("--SENT REQUEST "+hostname+"--");
			
			quorum_client.getO_out().writeObject(message);
			pendingRepliesToReceive.put(client_id, true);
			
			//ClientsServerThreadListener clientServer = new ClientsServerThreadListener(quorum_client);
			//Thread t = new Thread(clientServer);
			//t.start();
		}
		
		return true;
	}
	
	public void sendRelease() throws IOException {
		System.out.println("--send release to all--");
		for(Map.Entry<String, SocketMap> entry: allClientsSockets.entrySet()) {
			SocketMap quorum_client = entry.getValue();
			MutexMessage message = new MutexMessage(id, MessageType.RELEASE);
			quorum_client.getO_out().writeObject(message);
		}
		record.release += quorum.size();
	}
	
	public Message request(Operations operation) throws IOException, ClassNotFoundException {	
		//creating the request
		System.out.println("--sending request to server--");

		if(operation.getOperation().equals(OperationMethod.CREATE)) {
			Resource resource = operation.getInputResource();
			File file = new File(resource.getFilename());
			if(file.exists()) {
				String fileContent = "";
				BufferedReader br = new BufferedReader(new FileReader(file));
				String line = "";
				while((line = br.readLine()) != null ) {
					fileContent += line;
				}					
				resource.setFileContent(fileContent);
				br.close();
				
				operation.setInputResource(resource);
			}
		}
		
		//sends the operation request
		serverSocketMap.getO_out().writeObject(operation);
		
		//wait for the response
		Object object = serverSocketMap.getO_in().readObject();
		
		
		Message m;
		
		if (object instanceof Message) {
			 m = (Message)object;
			 return m;
		} else {
			return null;
		}
	}

	public int getId() {
		return id;
	}

	public SocketMap getServerSocketMap() {
		return serverSocketMap;
	}

	public Map<String, SocketMap> getQuorum() {
		return quorum;
	}


	public InetAddress getIp() {
		return ip;
	}

	public Integer getPort() {
		return port;
	}


	public Map<Integer, InetSocketAddress> getOtherClients() {
		return otherClients;
	}


	public Map<String, Integer> getHostIdMap() {
		return hostIdMap;
	}


}
