package agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;


import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.SimpleAchieveREInitiator;
import jade.proto.SubscriptionInitiator;


@SuppressWarnings("serial")
public class TourGuideAgent extends Agent {
	
	//map containing curator AID as key and interest-artifacts map as value
	private Hashtable<AID, Hashtable<String, String[]>> museumArtifactMap;
	
	private Hashtable<AID, ArrayList<String>> orderTable; 
	
	private ArrayList<AID> curators;
	
	private final static int MAX_CAPACITY = 5;	
	
	
	private int price;

	@Override
	protected void setup() {
		
		orderTable = new Hashtable<AID, ArrayList<String>>();
		museumArtifactMap = new Hashtable<AID, Hashtable<String, String[]>>();
		
		Object[] args = getArguments();
		if(args == null || args.length == 0){
			System.out.println("<" + getLocalName() + "> not invoked with price of the tour as argument");
			doDelete();
		}
		
		price = Integer.parseInt((String)args[0]);
		
		System.out.println("<" + getLocalName() + "> is ready !");
		// Register the tour service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("virtual-tour");
		sd.setName("smart-museum-tour");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		subscibeForCuratorService();
		
		// add cyclic behavior to keep listening requests from Profiler Agent
		addBehaviour(new OfferRequestsServer());
		
		//add cyclic behavior to receive orders from Profiler Agent
		addBehaviour(new PurchaseOrdersServer());		
		
	}

	private void subscibeForCuratorService() {
		// Build the description used as template for the subscription
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription templateSd = new ServiceDescription();
		templateSd.setType("museum-curator");
		template.addServices(templateSd);
		
		SearchConstraints sc = new SearchConstraints();
		// We want to receive 10 results at most
		sc.setMaxResults(new Long(10));
  		
		addBehaviour(new SubscriptionInitiator(this, DFService.createSubscriptionMessage(this, getDefaultDF(), template, sc)) {
			protected void handleInform(ACLMessage inform) {
  			System.out.println("<"+getLocalName()+">: Notification received from DF");
  			try {
					DFAgentDescription[] results = DFService.decodeNotification(inform.getContent());
		  		if (results.length > 0) {
		  			curators = new ArrayList<AID>();
		  			for (int i = 0; i < results.length; ++i) {
		  				DFAgentDescription dfd = results[i];
		  				AID provider = dfd.getName();
		  				Iterator it = dfd.getAllServices();
		  				while (it.hasNext()) {
		  					ServiceDescription sd = (ServiceDescription) it.next();
		  					if (sd.getType().equals("museum-curator")) {
	  							System.out.println("museum-curator service found:");
		  						System.out.println("- Service \""+sd.getName()+"\" provided by agent "+provider.getName());
		  						curators.add(dfd.getName());
		  					}
		  				}
		  			}
		  		}	
	  			System.out.println();
		  	}
		  	catch (FIPAException fe) {
		  		fe.printStackTrace();
		  	}
			}
		} );
  }

	@Override
	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
	}
		
	/**
	   Inner class OfferRequestsServer.
	   This is the behaviour used by Tour Guide agents to serve incoming requests 
	   for offer from Profiler Agents.
	   If the requesting profiler agent's intereste matches with the the local catalogue the virtual tour guide agent replies 
	   with a PROPOSE message specifying the price. Otherwise a REFUSE message is
	   sent back.
	 */
	private class OfferRequestsServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received.
				
				ACLMessage reply = msg.createReply();				
				if (orderTable.size() <= MAX_CAPACITY) {
					// The requested book is available for sale. Reply with the price
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(price));
					System.out.println("<" + getLocalName() + "> replying with a PROPOSE to " + msg.getSender() );
				}
				else {
					// The requested book is NOT available for sale.
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-a-match");
					System.out.println("<" + getLocalName() + "> replying with a REFUSE to " + msg.getSender() );
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer
	
	
	/**
	   Inner class PurchaseOrdersServer.
	   This is the behaviour used by Tour guide agents to serve incoming 
	   offer of acceptances (i.e. purchase orders) from profiler agents.
	   The Tour guide agent adds to the internal data structure to process orders
	   and replies with an INFORM message to notify the profiler that the
	   purchase has been successfully completed.
	 */
	
	private class PurchaseOrdersServer extends CyclicBehaviour {
		public void action() {
			//no action if there are no curators found yet
			if(curators == null || curators.size() == 0){
				return;
			}
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// ACCEPT_PROPOSAL Message received. Time to create and process Order
				String profile = msg.getContent();
				ArrayList<String> interests = new ArrayList<String>();
				for(String interest: profile.split(",")){
					interests.add(interest);
				}
				
				ACLMessage reply = msg.createReply();				
				orderTable.put(msg.getSender(), interests);			
				reply.setPerformative(ACLMessage.INFORM);
				System.out.println("<"+ getLocalName() + "> accepted order from profile agent " + msg.getSender().getName());				
				myAgent.send(reply);
				
				//query artifact lists from all curators
				SequentialBehaviour sb = new SequentialBehaviour(myAgent);
				ParallelBehaviour pb = new ParallelBehaviour(myAgent, ParallelBehaviour.WHEN_ALL);
				for (AID aid : curators) {
					ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
					request.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
					request.addReceiver(aid);
					request.setContent("getAllArtifacts");
					pb.addSubBehaviour(new CuratorRequest(myAgent, request));
				}
				sb.addSubBehaviour(pb);
				//send the response to Profiler
				sb.addSubBehaviour(new SendToProfiler(msg.getSender()));
				
				myAgent.addBehaviour(sb);
				
			}
			else {
				block();
			}
		}
	}  // End of inner class PurchaseOrdersServer
	
	
	
	/**
	 * 
	 * Handles responses containing artifact list from Curator agents
	 *
	 */
	private class CuratorRequest extends SimpleAchieveREInitiator {

		 @Override
		protected ACLMessage prepareRequest(ACLMessage msg) {
			System.out.println("<" + myAgent.getLocalName() + ">: sending request to get artifact list for the catalog to the museum");
			return super.prepareRequest(msg);
		}
		public CuratorRequest(Agent a, ACLMessage msg) {
			super(a, msg);
			// TODO Auto-generated constructor stub
		}
		
		 @Override
		protected void handleInform(ACLMessage msg) {
			
			Hashtable<String, String[]> artifacts = null;		
			try {
				artifacts = (Hashtable<String, String[]>) (msg.getContentObject());
					
			} catch (UnreadableException ex) {
				System.out.println("<" + myAgent.getLocalName() + ">: no element found in the message");
				
			}			
			museumArtifactMap.put(msg.getSender(), artifacts);			
			System.out.println("<" + myAgent.getLocalName() + ">: recieved artifacts " + artifacts);
		}
		 
		@Override
		protected void handleNotUnderstood(ACLMessage msg) {
			System.out.println("<" +myAgent.getLocalName() + ">: recieved unknown reply " + msg.getContent());
		}		
		
	}
	
	/**
	 * 
	 * One shot behavior to send virtual tour to the profiler agent
	 *
	 */
	public class SendToProfiler extends OneShotBehaviour {
		
		private AID profiler;

	    public SendToProfiler(AID profilerAgent) {
	    	super();
	    	profiler = profilerAgent;	                
	    }

	    @Override
	    public void action() {
	        System.out.println("<" + myAgent.getLocalName() + ">: sending virtual tour to Profiler " + profiler);
	        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
	        inform.setConversationId("create-tour");
	        inform.setProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST);
	        inform.addReceiver(profiler);
	        
	        Hashtable<AID, String[]> responseMap = new Hashtable<AID, String[]>();
	        //get profiler's interests
	        ArrayList<String> interests = orderTable.get(profiler);
	        
	        //find artifacts matching profiler's interests in every museum	        
	        for(AID museum : Collections.list(museumArtifactMap.keys())) {
	        	for(String interest : interests){
	        		Hashtable table = museumArtifactMap.get(museum);
	        		String[] artifactList = (String[]) table.get(interest); 
	        		
	        		//put into the response map
	        		if(artifactList.length >0){
	        			responseMap.put(museum, artifactList);
	        		}
	        	}
	        }
	        
	        try {
	        	
	            inform.setContentObject(responseMap);
	        } catch (IOException ex) {
	            ex.printStackTrace();
	        }
	        myAgent.send(inform);
	    }
	}
	

}
