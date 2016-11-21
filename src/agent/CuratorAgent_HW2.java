package agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
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
public class CuratorAgent_HW2 extends Agent{
	
	private int price;
	boolean auction=false;
	AID auctioneer;

	@Override
	protected void setup() {
		
		Object[] args = getArguments();
		if(args == null || args.length == 0){
			System.out.println("<" + getLocalName() + "> must be invoked with price");
			doDelete();
		}
		else{
			price=Integer.parseInt((String)args[0]);
		}
		
		// Register the bidding service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("bidding");
		sd.setName("smart-museum-bidding");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}				
		System.out.println("<" + getLocalName() + "> is ready !");
		

		addBehaviour(new BidderBehaviour());

		
	}
	
	private class ReceiveInformStartOfAuctionBehaviour extends OneShotBehaviour{

		@Override
		public void action(){
			MessageTemplate mt= MessageTemplate.MatchPerformative(ACLMessage.INFORM);
			ACLMessage message = myAgent.receive(mt);
			System.out.println("<" + getLocalName() + "> received start of auction "+message.getContent()+" from agent "+message.getSender().getLocalName());	
		}
	}
	
	private class BidderBehaviour extends CyclicBehaviour {
		@Override
		public void action(){
			MessageTemplate mt= MessageTemplate.MatchAll();
			ACLMessage message = myAgent.receive(mt);
			if (message != null){
				auctioneer=message.getSender();
				switch (message.getPerformative()){
				case ACLMessage.INFORM:{
					if (message.getContent().equals("Mona-Lisa")){
						System.out.println("<" + getLocalName() + "> received start of auction "+message.getContent()+" from agent "+message.getSender().getLocalName());
						auction=true;
					}
					else{
						System.out.println("<" + getLocalName() + "> received end of  "+message.getContent()+" from agent "+message.getSender().getLocalName());
						block();
					}
					break;
				}
				case ACLMessage.CFP:{
					//If the bid proposed by the auctioneer is below the buyers max price, propose to buy it
					try {
						if ((Integer) message.getContentObject() <= price){
							ACLMessage reply = message.createReply();
							reply.setPerformative(ACLMessage.PROPOSE);
							try {
								reply.setContentObject(message.getContentObject());
							} 
							catch (IOException e) {
								e.printStackTrace();
							}
							myAgent.send(reply);
							System.out.println("<" + getLocalName() + "> making a proposal to buy item for "+message.getContentObject());

						}
						// otherwise don't accept it
						else{
							//do nothing
							System.out.println("<" + getLocalName() + "> not accepting offer "+message.getContentObject());

						}
					} catch (UnreadableException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;
				}
				case ACLMessage.ACCEPT_PROPOSAL:{
					System.out.println("<" + getLocalName() + "> Proposal accepted by "+auctioneer.getLocalName());
					block();

					break;
				}
				case ACLMessage.REJECT_PROPOSAL:{
					System.out.println("<" + getLocalName() + "> Proposal rejected by "+auctioneer.getLocalName());

					break;
				}
				}
			}
		}
	}
	
@Override
	protected void takeDown() {
		
		super.takeDown();
	}
	
	

}
