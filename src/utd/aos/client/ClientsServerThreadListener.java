package utd.aos.client;

import java.io.ObjectInputStream;
import utd.aos.utils.MutexMessage;
import utd.aos.utils.MutexMessage.MessageType;
import utd.aos.utils.SocketMap;

public class ClientsServerThreadListener extends Client {
	
	SocketMap socketmap;
	public ClientsServerThreadListener(SocketMap socketmap) {
		this.socketmap = socketmap;
	}
	
	@Override
	public void run() {
		try {
			ObjectInputStream o_in = socketmap.getO_in();

			MutexMessage message = (MutexMessage)o_in.readObject();
			if(message.getType().equals(MessageType.FAILED)) {
				gotFailedMessageFrom.put(message.getId(), true);
			}
			
			if(message.getType().equals(MessageType.REPLY) 
					&& pendingRepliesToReceive.containsKey(message.getId())) {
				System.out.println("--got reply from "+socketmap.getAddr().getHostName()+"--");
				pendingRepliesToReceive.remove(message.getId());
				if(sentYieldMessageTo.containsKey(message.getId())) {
					sentYieldMessageTo.remove(message.getId());
				}
				if(pendingRepliesToReceive.size() == 0) {
					gotallReplies.release();
					System.out.println("--releasing allreply mutex--");
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
