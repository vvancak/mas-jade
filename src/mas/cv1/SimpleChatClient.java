package mas.cv1;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

public class SimpleChatClient extends Agent {

    @Override
    protected void setup() {
        super.setup();

        this.addBehaviour(new MsgSendingBehavior(this));
    }

    @Override
    protected void takeDown() {
        super.takeDown();
    }

    class MsgSendingBehavior extends TickerBehaviour {

        public MsgSendingBehavior(Agent a) {
            super(a, 5000);
        }

        @Override
        public void onTick() {

            ServiceDescription sd = new ServiceDescription();
            sd.setType("messaging-server");

            DFAgentDescription dfd = new DFAgentDescription();
            dfd.addServices(sd);

            try {
                DFAgentDescription[] servers = DFService.search(myAgent, dfd);
                if (servers.length == 0) {
                    System.err.println("No servers found");
                    return;
                }
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
