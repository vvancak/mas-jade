package mas.cv1;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;


//jednoduchy agent, ktery kazdych 5 vterin posila zpravu prvnimu serveru, ktery najde
public class SimpleChatClient extends Agent {

    @Override
    protected void setup() {
        super.setup();

        //pridani chovani klienta -- posila zpravu serveru kazdych 5 vterin
        this.addBehaviour(new MsgSendingBehavior(this));
    }

    @Override
    protected void takeDown() {
        super.takeDown();
    }

    //jednoduche chovani, ktere kazdych 5 vterin posle zpravu serveru
    class MsgSendingBehavior extends TickerBehaviour {

        public MsgSendingBehavior(Agent a) {
            super(a, 5000);
        }

        @Override
        public void onTick() {

            //vytvoreni popisu sluzby serveru
            ServiceDescription sd = new ServiceDescription();
            sd.setType("messaging-server");

            //vytvoreni popisu agenta, ktery sluzbu poskytuje
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.addServices(sd);

            try {
                //vyhledani vsech serveru
                DFAgentDescription[] servers = DFService.search(myAgent, dfd);
                if (servers.length == 0) {
                    System.err.println("No servers found");
                    return;
                }
                //vytvoreni zpravy a poslani prvnimu serveru ze seznamu
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
