package agent;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class TourGuideAgent extends Agent {

	@Override
	protected void setup() {
		
		System.out.println("Tour Guide agent " + getAID().getName() + " is ready !");
		// Register the book-selling service in the yellow pages
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
		
		// add cyclic behavior to keep listening requests from ProfilerAgent
		addBehaviour(new CyclicBehaviour() {
			@Override
			public void action() {
				// TODO Auto-generated method stub
				
			}
		});
		
		//add tick behavior to periodically look up new items from CuratorAgent
		addBehaviour(new TickerBehaviour(this, 60000) {
			
			@Override
			protected void onTick() {
				// look up curator agent from yellow pages
				// Send a message to get the list of new items
				
			}
		});
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

}
