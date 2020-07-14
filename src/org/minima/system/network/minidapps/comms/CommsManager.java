package org.minima.system.network.minidapps.comms;

import java.util.ArrayList;

import org.minima.system.Main;
import org.minima.system.SystemHandler;
import org.minima.system.network.NetworkHandler;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONObject;
import org.minima.utils.messages.Message;

public class CommsManager extends SystemHandler {

	public static final String COMMS_INIT = "COMMS_INIT";
	
	public static final String COMMS_START        = "COMMS_STARTSERVER";
	public static final String COMMS_NEWSERVER    = "COMMS_NEWSERVER";
	public static final String COMMS_SERVERERROR  = "COMMS_SERVERERROR";
	public static final String COMMS_STOP         = "COMMS_STOPSERVER";
	
	public static final String COMMS_BROADCAST    = "COMMS_BROADCAST";
	
	public static final String COMMS_CONNECT      = "COMMS_CONNECT";
	public static final String COMMS_DISCONNECT   = "COMMS_DISCONNECT";
	public static final String COMMS_SEND         = "COMMS_SEND";
	
	public static final String COMMS_NEWCLIENT    = "COMMS_NEWCLIENT";
	public static final String COMMS_CLIENTSHUT  = "COMMS_CLIENTERROR";
	
	ArrayList<CommsServer> mServers;
	ArrayList<CommsClient> mClients;
	
	public CommsManager(Main zMain) {
		super(zMain, "COMMSMANAGER");
	
		mServers = new ArrayList<>();
		mClients = new ArrayList<>();
		
		PostMessage(COMMS_INIT);
	}
	
	public ArrayList<CommsServer> getServers(){
		return mServers;
	}
	
	public ArrayList<CommsClient> getClients(){
		return mClients;
	}
	
	public CommsServer getServer(int zPort) {
		for(CommsServer server : mServers) {
			if(server.getPort() == zPort) {
				return server;
			}
		}
		
		return null;
	}
	
	public CommsClient getClient(String zUID) {
		for(CommsClient client : mClients) {
			if(client.getUID().equals(zUID)) {
				return client;		
			}
		}
	
		return null;
	}
	
	public void shutdown() {
		//Shut down the servers
		for(CommsServer server : mServers) {
			server.stop();	
		}
		
		//Shut down any clients
		for(CommsClient client : mClients) {
			client.PostMessage(CommsClient.COMMSCLIENT_SHUTDOWN);	
		}
		
		stopMessageProcessor();
	}
	
	@Override
	protected void processMessage(Message zMessage) throws Exception {
		
		MinimaLogger.log("CommsManager : "+zMessage);
		
		
		if(zMessage.getMessageType().equals(COMMS_INIT)) {
		
			
		}else if(zMessage.getMessageType().equals(COMMS_START)) {
			//the details
			String minidapp = zMessage.getString("minidappid");
			int port = zMessage.getInteger("port");
		
			//Now create one..
			CommsServer server = new CommsServer(port, this);
			
		}else if(zMessage.getMessageType().equals(COMMS_NEWSERVER)) {
			//Get the Server
			CommsServer server = (CommsServer) zMessage.getObject("server");
			
			//Add to our List
			mServers.add(server);
			
			//Broadcast..
			JSONObject netaction = new JSONObject();
			netaction.put("action", "newserver");
			netaction.put("port", server.getPort());
			postCommsMssage(netaction);
			
		}else if(zMessage.getMessageType().equals(COMMS_SERVERERROR)) {
			//Get the Server
			CommsServer server = (CommsServer) zMessage.getObject("server");
			
			//Add to our List
			mServers.remove(server);
			
			//Broadcast..
			JSONObject netaction = new JSONObject();
			netaction.put("action", "server_error");
			netaction.put("port", server.getPort());
			netaction.put("error", zMessage.getString("error"));
			postCommsMssage(netaction);
			
		}else if(zMessage.getMessageType().equals(COMMS_STOP)) {
			int port = zMessage.getInteger("port");
		
			//Stop that server
			CommsServer server = getServer(port);
			if(server != null) {
				server.stop();	
			
				//Remove from the list..
				mServers.remove(server);
			
				//Broadcast..
				JSONObject netaction = new JSONObject();
				netaction.put("action", "server_stop");
				netaction.put("port", server.getPort());
				postCommsMssage(netaction);
			}
			
		}else if(zMessage.getMessageType().equals(COMMS_CONNECT)) {
			String hostport = zMessage.getString("hostport");
			int index = hostport.indexOf(":");
			String host = hostport.substring(0, index);
			int port = Integer.parseInt(hostport.substring(index+1).trim());
			
			//Check for an old one..
			for(CommsClient client : mClients) {
				if(client.isOutBound()) {
					if(client.getHost().equals(host) && client.getPort() == port) {
						//Already connected..
						return;
					}
				}
			}
			
			//Start a new Client..
			CommsClient client = new CommsClient(host, port, this);
		
		}else if(zMessage.getMessageType().equals(COMMS_DISCONNECT)) {
			String uid = zMessage.getString("uid");
			
			//Get the Client..
			for(CommsClient client : mClients) {
				if(client.getUID().equals(uid)) {
					client.PostMessage(CommsClient.COMMSCLIENT_SHUTDOWN);
				}
			}
		
		}else if(zMessage.getMessageType().equals(COMMS_CLIENTSHUT)) {
			//There's a new client connected to a comms server
			CommsClient client = (CommsClient) zMessage.getObject("client");
			
			//Add to our List..
			mClients.remove(client);	
			
			postClientMessage("client_shut",client);
			
		}else if(zMessage.getMessageType().equals(COMMS_NEWCLIENT)) {
			//There's a new client connected to a comms server
			CommsClient client = (CommsClient) zMessage.getObject("client");
			
			//Add to our List..
			mClients.add(client);	
			
			postClientMessage("client_new",client);
			
		}else if(zMessage.getMessageType().equals(COMMS_SEND)) {
			String uid     = zMessage.getString("uid");
			String message = zMessage.getString("message");
			
			CommsClient client = getClient(uid);
			if(client!= null) {
				client.postSend(message);	
			}
			
		}else if(zMessage.getMessageType().equals(COMMS_BROADCAST)) {
			String message = zMessage.getString("message");
			int port = zMessage.getInteger("port");
			
			for(CommsClient client : mClients) {
				if(client.isInBound() && client.getPort() == port) {
					client.postSend(message);
				}
			}
		}
	}
	
	public void postClientMessage(String zAction, CommsClient zClient) {
		//Already connected..
		JSONObject netaction = new JSONObject();
		netaction.put("action", zAction);
		
		netaction.put("host", zClient.getHost());
		netaction.put("port", zClient.getPort());
		netaction.put("uid", zClient.getUID());
		netaction.put("outbound", zClient.isOutBound());
		
		postCommsMssage(netaction);
	}
	
	public void postCommsMssage(JSONObject zMessage) {
		//someone has connected to a port you opened..
		JSONObject newclient = new JSONObject();
		newclient.put("event","network");
		newclient.put("details",zMessage);
		
		Message msg = new Message(NetworkHandler.NETWORK_WS_NOTIFY);
		msg.addString("message", newclient.toString());
		
		//Post to the Network..
		getMainHandler().getNetworkHandler().PostMessage(msg);
	}

}