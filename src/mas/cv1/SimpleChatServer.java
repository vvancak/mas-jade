package mas.cv1;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.Date;

//a simple agent which registers itself to the DF, prints received messages and forwards themto other agents
public class SimpleChatServer extends Agent {

    @Override
    protected void setup() {
        super.setup();

        //messaging-server service description
        ServiceDescription sd = new ServiceDescription();
        sd.setType("messaging-server");
        sd.setName("server");

        //description of this agents and the services it provides
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(this.getAID());
        dfd.addServices(sd);

        //registration to DF
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        //add behavior which takes care of the receiving and forwarding of messages
        this.addBehaviour(new MessageReceivingBehaviour());
    }

    @Override
    protected void takeDown() {
        super.takeDown();

        //derigister at the end
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }


    //a behavior which receives, prints and forwards the messages
    class MessageReceivingBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg == null) {
                block();
                return;
            }

            //print the received message
            System.out.println("[" + new Date().toString() + "] " + msg.getSender().getName() + ": " + msg.getContent());

            //description of the messaging-client service (for searching)
            ServiceDescription sd = new ServiceDescription();
            sd.setType("messaging-client");

            //description of the client agents
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.addServices(sd);

            try {

                //search for the clients
                DFAgentDescription[] clients = DFService.search(myAgent, dfd);

                //create the message for the clients
                ACLMessage toClients = new ACLMessage(ACLMessage.INFORM);
                toClients.setContent(msg.getSender().getName() + ": " + msg.getContent());

                //add all clients to the recipients and send the message
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
