package agent;


import java.io.IOException;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.proto.ContractNetInitiator;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

@SuppressWarnings("serial")
public class ArtistManager extends Agent {
	
	AID[] bidders;
	private int currentPrice; //the initial price
	private int reservePrice; //minimum acceptable price
	private int reductionRate;
	boolean auction;
	String artwork;
	CyclicBehaviour c;
	AID winner;
	
	@Override
	protected void setup(){
		Object[] args = getArguments();
		if(args == null || args.length == 0){
			artwork = "Mona-Lisa";
			currentPrice = 200;
			reservePrice = 120;
			reductionRate =10;
			
			System.out.println("<" + getLocalName() + "> invoked without arguments, default values are used");
		}
		else{
			artwork = (String)args[0];
			currentPrice = Integer.parseInt((String)args[1]);
			reservePrice = Integer.parseInt((String)args[2]);
			reductionRate =Integer.parseInt((String)args[3]);
		}
		
		System.out.println("<" + getLocalName() + "> is ready !");
		
		SequentialBehaviour sb = new SequentialBehaviour();
		sb.addSubBehaviour(new SearchBidderAgentBehaviour());
		sb.addSubBehaviour(new SendInformStartOfAuctionBehaviour() );
		sb.addSubBehaviour(new AuctioneerBehaviour());
		
		addBehaviour(sb);
		
	}
	
	
	/**
	 * 
	 * A simple behaviour class which keeps looking for Bidder Agent until it finds one.
	 *
	 */
	private class SearchBidderAgentBehaviour extends SimpleBehaviour {

		@Override
		public void action() {
			
			// search for bidder agents
			DFAgentDescription dfd = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("bidding");
			dfd.addServices(sd);
			DFAgentDescription[] result = null;
			try {
				result = DFService.search(myAgent, dfd);
			} catch (FIPAException ex) {
				ex.printStackTrace();
			}
			System.out.println("<" + getLocalName() + "> : found following " + result.length + " bidders");
			if (result.length > 0) {
				bidders = new AID[result.length];
				
				for (int i = 0; i < result.length; ++i) {
					bidders[i] = result[i].getName();
					System.out.println(bidders[i].getName());
				}				
			}

		}
		
		@Override
		public boolean done() {
			if (bidders != null && bidders.length >0){
				return true;
			}
			else{
				return false;
			}
		}
		
		
	}  // End of inner class SearchBidderAgentBehaviour
	
	private class SendInformStartOfAuctionBehaviour extends OneShotBehaviour{

		@Override
		public void action(){
			ACLMessage message = new ACLMessage(ACLMessage.INFORM);

			for (AID bidder : bidders) {
				message.addReceiver(bidder);
			}

			message.setContent(""+artwork);

			myAgent.send(message);
			
			System.out.println("<" + myAgent.getLocalName() + "> sent the auction start message  " );
			
			auction=true;
			
		}
	}
	
	private class AuctioneerBehaviour extends ParallelBehaviour{
		public AuctioneerBehaviour() {		
			super(ParallelBehaviour.WHEN_ALL);
		
		// Add a ticker behaviour that will propose a new bid every second.
				addSubBehaviour(new TickerBehaviour(myAgent, 1000) {

					@Override
					protected void onTick() {
						// Ticker will be canceled if the auctions status is not ongoing.
						if (!auction) {
							System.out.println("<" + myAgent.getLocalName() +">: End of auction - Current bid is " + currentPrice+" - Winner is "+winner.getLocalName());

							ACLMessage message = new ACLMessage(ACLMessage.INFORM);

							for (AID bidder : bidders) {
								message.addReceiver(bidder);
							}

							message.setContent("auction "+artwork);

							myAgent.send(message);
							stop();
							removeSubBehaviour(c);

							return;
						}

						currentPrice = currentPrice - 10;

						// Send CFP if it is not below the minimum price.
						if (currentPrice >= reservePrice) {
							ACLMessage message = new ACLMessage(ACLMessage.CFP);

							for (AID bidder : bidders) {
								message.addReceiver(bidder);
							}

							try {
								message.setContentObject(currentPrice);
							} 
							catch (IOException e) {
								e.printStackTrace();
							}
							myAgent.send(message);
							
							System.out.println("<" + myAgent.getLocalName() +">: Sending a new CFP with price: " + currentPrice);

						}
						// If current price is below minimum price, inform the participants the auction ended without winner.
						else {
							System.out.println("<" + myAgent.getLocalName() +">: End of auction without winner ");

							ACLMessage message = new ACLMessage(ACLMessage.INFORM);

							for (AID bidder : bidders) {
								message.addReceiver(bidder);
							}

							message.setContent("auction "+artwork);

							myAgent.send(message);
							stop();
						}
					}
		});
		
		addSubBehaviour(c= new CyclicBehaviour() {
			
			@Override
			public void action(){
				MessageTemplate mt= MessageTemplate.MatchAll();
				ACLMessage message = myAgent.receive(mt);
				
				if (message != null){
					switch (message.getPerformative()) {
					
					case ACLMessage.PROPOSE:{
						if (auction){
							try {
								if (currentPrice == (Integer) message.getContentObject()){
									winner=message.getSender();
									System.out.println("<" + myAgent.getLocalName() +">: Proposal from bidder "+winner.getLocalName()+  " accepted for " + currentPrice);


									ACLMessage reply = message.createReply();
									reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);

									try {
										reply.setContentObject(currentPrice);
									} 
									catch (IOException e) {
										e.printStackTrace();
									}
									myAgent.send(reply);
									auction=false;
									
									
								}
								else{
									ACLMessage reply = message.createReply();
									reply.setPerformative(ACLMessage.REJECT_PROPOSAL);

									try {
										reply.setContentObject(currentPrice);
									} 
									catch (IOException e) {
										e.printStackTrace();
									}
									myAgent.send(reply);
									
									System.out.println("<" + myAgent.getLocalName() +">: Proposal from bidder "+message.getSender().getLocalName()+  " rejected ");

								}
							} catch (UnreadableException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						else{
							ACLMessage reply = message.createReply();
							reply.setPerformative(ACLMessage.REJECT_PROPOSAL);

							reply.setContent("Auction ended");
						
							myAgent.send(reply);
							System.out.println("<" + myAgent.getLocalName() +">: Proposal from bidder "+message.getSender().getLocalName()+  " rejected - auction already ended ");
						

							
						}
						break;
					}
					case ACLMessage.NOT_UNDERSTOOD:{
						//Just wait next offer
						System.out.println("<" + myAgent.getLocalName() +">: Not understood message from bidder "+winner.getLocalName()+  " - wait the next offer ");
						break;

					}
					
					
					}
				}
				else{
					block();
				}
				
			}
			
		});
				
				
				
				
	}
		
}
	
	
	@Override
	protected void takeDown() {
		
		super.takeDown();
	}
}
