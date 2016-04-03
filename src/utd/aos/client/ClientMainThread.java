package utd.aos.client;

import utd.aos.utils.Operations;

public class ClientMainThread extends Client {
	
	Operations operation;
	
	public ClientMainThread(Operations operation) {
		this.operation = operation;
	}
	
	@Override
	public void run() {
		try {
			inprocess = true;
			if(getMutex()) {
				//System.out.println("--WAIT allreply sema ( Main thread)--");			
				//gotallReplies.acquire();

				//System.out.println("--WAIT release sema ( Main thread)--");			
				//gotallReleases.acquire();

				while(pendingRepliesToReceive.size() != 0 || pendingReleaseToReceive != 0) {
					/*
					if(pendingReleaseToReceive != 0)
						System.out.println("---WAITING for all RELEASE");
					else
						System.out.println("---WAITING for all REPLIES");
					*/
					Thread.sleep(20);
					
				}
				
				pendingReleaseToReceive = id;

				System.out.println("--starting CS--");
				request(operation);
				System.out.println("--Exiting CS--");

				//System.out.println("--RELEASE release sema (Main thread)--");
				//gotallReleases.release();

				//System.out.println("--RELEASE allreply sema (Main thread)--");
				//gotallReplies.release();
				pendingReleaseToReceive = 0;			
				sendRelease();
				//request_fifo.remove();

			}
			inprocess = false;
			
		} catch (Exception e) {

		}
	}
}
