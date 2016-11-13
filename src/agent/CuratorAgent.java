package agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

@SuppressWarnings("serial")
public class CuratorAgent extends Agent{
	
	private Hashtable<String, String[]> interestArtifactsMap;
	private Hashtable<String, Artifact> artifactMap;

	@Override
	protected void setup() {
		createArtifactStore();
		
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("museum-curator");	
		sd.setName("museum-London");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		System.out.println("<" + getLocalName() + "> is ready !");
		
		addBehaviour(new CuratorServiceBehaviour());
		
	}
	
	private class CuratorServiceBehaviour extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// Request Message received.
				String content = msg.getContent();
				ACLMessage reply = msg.createReply();
				System.out.println("<" + myAgent.getLocalName() + "> received request with content : " + content);
				if(content.equals("getAllArtifacts")){
					//return all artifacts to sender
					reply.setPerformative(ACLMessage.INFORM);
					try {
						reply.setContentObject(interestArtifactsMap);
						System.out.println("<" + myAgent.getLocalName() + "> replying to \"getAllArtifacts\" message to " + msg.getSender() );
					} catch (IOException e) {						
						e.printStackTrace();
					}
					
				} else if(msg.getConversationId() != null && msg.getConversationId().equals("getArtifactDetail")){
					reply.setPerformative(ACLMessage.INFORM);
					try {
						String[] artifacts  = (String[]) msg.getContentObject();
						ArrayList<Artifact> list = new ArrayList<Artifact>();
						for(String artifactId : artifacts){
							list.add(artifactMap.get(artifactId));
						}					
						reply.setContentObject(list);
					} catch (IOException e) {						
						e.printStackTrace();
					} catch (UnreadableException e1) {						
						e1.printStackTrace();
					}
				}
				else {
					// unknown message
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-a-match");
					System.out.println("<" + myAgent.getLocalName() + "> replying with a REFUSE to " + msg.getSender() );
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}  // End of inner class OfferRequestsServer

	private void createArtifactStore() {
		interestArtifactsMap = new Hashtable<String, String[]>();
		interestArtifactsMap.put("Nature", new String[]{"forest","river","mountain"});
		interestArtifactsMap.put("History", new String[]{"castle","sword","manuscript"});		
		interestArtifactsMap.put("Science", new String[]{"computer","microscope"});
		
		artifactMap = new Hashtable<String, Artifact>();
		artifactMap.put("forest", new Artifact("1", "a forest", "unknown", "21st century", "painting"));
		artifactMap.put("river", new Artifact("2", "a river", "unknown", "21st century", "painting"));
		artifactMap.put("mountain", new Artifact("3", "a mountain", "unknown", "1989", "photograph"));
		
		artifactMap.put("castle", new Artifact("4", "german castle", "unknown", "12th century", "replica"));
		artifactMap.put("sword", new Artifact("5", "chinese sword", "ganghes khan", "13th century", "original"));
		artifactMap.put("manuscript", new Artifact("6", "first book", "unknown", "14th century", "book"));		
		
		artifactMap.put("computer", new Artifact("7", "super computer", "Charles Babbage", "19th century", "original"));
		artifactMap.put("microscope", new Artifact("8", "electron microscoope", "unknown", "21st century", "original"));
		
	}

	@Override
	protected void takeDown() {
		
		super.takeDown();
	}
	
	

}
