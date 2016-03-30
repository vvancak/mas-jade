package mas.cv1;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;


//a simple agent which sends "hi" message every 5 seconds to the messaging server
public class SimpleChatClient extends Agent {

    @Override
    protected void setup() {
        super.setup();

        //add the sending behavior
        this.addBehaviour(new MsgSendingBehavior(this));
    }

    @Override
    protected void takeDown() {
        super.takeDown();
    }

    //simple behavior which sends a message every 5 seconds
    class MsgSendingBehavior extends TickerBehaviour {

        public MsgSendingBehavior(Agent a) {
            super(a, 5000);
        }

        @Override
        public void onTick() {

            //messaging-server service description
            ServiceDescription sd = new ServiceDescription();
            sd.setType("messaging-server");

            //server agent description
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.addServices(sd);

            try {
                //search for all servers
                DFAgentDescription[] servers = DFService.search(myAgent, dfd);
                if (servers.length == 0) {
                    System.err.println("No servers found");
                    return;
                }
                //create the message and send it to the first server in the list
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(servers[0].getName());
                msg.setContent("Hi");
                myAgent.send(msg);
            } catch (FIPAException e) {
                e.printStackTrace();
            }

        }
    }
}
