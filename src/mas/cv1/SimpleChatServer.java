package mas.cv1;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.Date;

public class SimpleChatServer extends Agent {

    @Override
    protected void setup() {
        super.setup();

        ServiceDescription sd = new ServiceDescription();
        sd.setType("messaging-server");
        sd.setName("server");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(this.getAID());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        this.addBehaviour(new MessageReceivingBehaviour());
    }

    @Override
    protected void takeDown() {
        super.takeDown();

        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    class MessageReceivingBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg == null)
                return;

            System.out.println("[" + new Date().toString() + "] " + msg.getSender().getName() + ": " + msg.getContent());

            ServiceDescription sd = new ServiceDescription();
            sd.setType("messaging-client");

            DFAgentDescription dfd = new DFAgentDescription();
            dfd.addServices(sd);

            ACLMessage toClients = new ACLMessage(ACLMessage.INFORM);
            toClients.setContent(msg.getSender().getName() + ": " + msg.getContent());

            try {
                DFAgentDescription[] clients = DFService.search(myAgent, dfd);
                for (DFAgentDescription client : clients) {
                    toClients.addReceiver(client.getName());
                }
                myAgent.send(toClients);
            } catch (FIPAException e) {
                e.printStackTrace();
            }

            block();
        }
    }

}
