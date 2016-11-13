package agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;

import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;

import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;


@SuppressWarnings("serial")
public class ProfilerAgent extends Agent {
	
	AID[] tourGuides;
	String interests;
	private AID bestSeller; // The agent who provides the best offer 
	private int bestPrice;  // The best offered price
	private int repliesCnt = 0; // The counter of replies from seller agents
	private Hashtable<AID, String[]> responseMap;

	@Override
	protected void setup() {
		
		Object[] args =  getArguments();
		if(args == null || args.length <1) {
			System.out.println("<" + getLocalName() + "> not provided comma separated list of interests");
			doDelete();
		}
		interests = (String)args[0];
		System.out.println("<" + getLocalName() + "> is ready !");
		SequentialBehaviour sb = new SequentialBehaviour();
		sb.addSubBehaviour(new SearchTourAgentBehaviour());
		sb.addSubBehaviour(new GetVirtualTourBehaviour());
		
		addBehaviour(sb);
	}

	@Override
	protected void takeDown() {		
		super.takeDown();
	}
	
	/**
	 * 
	 * A simple behaviour class which keeps looking for Tour Agent until it finds one.
	 *
	 */
	private class SearchTourAgentBehaviour extends SimpleBehaviour {

		@Override
		public void action() {
			
			// search for tour guide agents
			DFAgentDescription dfd = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("virtual-tour");
			dfd.addServices(sd);
			DFAgentDescription[] result = null;
			try {
				result = DFService.search(myAgent, dfd);
			} catch (FIPAException ex) {
				ex.printStackTrace();
			}
			System.out.println("<" + getLocalName() + "> : found following " + result.length + " tour guides");
			if (result.length > 0) {
				tourGuides = new AID[result.length];
				
				for (int i = 0; i < result.length; ++i) {
					tourGuides[i] = result[i].getName();
					System.out.println(tourGuides[i].getName());
				}				
			}

		}

		@Override
		public boolean done() {
			if (tourGuides != null && tourGuides.length >0)
				return true;
			else
				return false;
		}
		
		
	}  // End of inner class SearchTourAgentBehaviour
	
	
	/**
	 * 
	 * A simple behaviour class which keeps looking for Tour Agent until it finds one.
	 *
	 */
	private class GetVirtualTourBehaviour extends Behaviour {
		
		private int step = 0;
		private MessageTemplate mt; // The template to receive replies

		@Override
		public void action() {
			
			switch (step) {
			case 0:
				//send CFP message to tour guide to get a quote
				ACLMessage request = new ACLMessage(ACLMessage.CFP);
				request.setContent("");
				for(int i =0; i < tourGuides.length ; ++i ){
					request.addReceiver(tourGuides[i]);
				}				
				request.setConversationId("tour-quote");
				System.out.println("<" + myAgent.getLocalName() + 
						"> sending CFP message to Tour Guide agents.");
				myAgent.send(request);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("tour-quote"),
						MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
				step = 1;
				break;
			case 1:
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer 
						int price = Integer.parseInt(reply.getContent());
						if (bestSeller == null || price < bestPrice) {
							// This is the best offer at present
							bestPrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= tourGuides.length) {
						System.out.println("<" + myAgent.getLocalName() + "> received replies to CFP from all Tour guide agents");
						// We received all replies
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// Send the purchase order to the seller that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(interests);
				order.setConversationId("create-tour");
				System.out.println("<" + myAgent.getLocalName() + "> sending ACCEPT PROPOSAL to " + bestSeller.getLocalName());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("create-tour"),
						MessageTemplate.MatchPerformative(ACLMessage.INFORM));
				step = 3;
				break;
			case 3:			
				reply = myAgent.receive(mt);
				if (reply != null) {					
					try {
						responseMap = (Hashtable<AID, String[]>) reply.getContentObject();
					} catch (UnreadableException e) {
						System.out.println("<" + getLocalName() +  "> Exception reading the response to purchase order message");
						e.printStackTrace();
					}					
					
					if(responseMap != null){
					System.out.println("<" + myAgent.getLocalName() + "> received following virtual tour - " + responseMap);
					// query every curator for artifact list
					for(AID museum : Collections.list(responseMap.keys())){
						ACLMessage getDetail = new ACLMessage(ACLMessage.REQUEST);
						getDetail.setConversationId("getArtifactDetail");
						String[] artifacts = responseMap.get(museum);
						try {
							getDetail.setContentObject(artifacts);
						} catch (IOException e) {							
							e.printStackTrace();
						}
						getDetail.addReceiver(museum);												
						myAgent.send(getDetail);						
					}
					step = 4;
					mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				}
				}
				else {
					block();
				}
				break;
			case 4:
				reply = myAgent.receive(mt);
				if(reply != null){
					try {
						ArrayList<Artifact> list = (ArrayList<Artifact>) reply.getContentObject();
						System.out.println("<" + getLocalName() + "> received following artifact list from Curator " + reply.getSender().getName() );
						for(Artifact artf : list) {
							System.out.println(artf.toString());
						}						
						
					} catch (UnreadableException e) {
						e.printStackTrace();
					}
					step = 5;
				}else {
					block();
				}				
				break;
			}
			

		}
		@Override
		public boolean done() {			
				return (step ==5);
		}		
		
	}  // End of inner class SearchTourAgentBehaviour

}
