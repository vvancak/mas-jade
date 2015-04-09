package mas.cv1;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.Date;

//jednoduchy agent, ktery se zaregistruje s DF a vypisuje prijate zpravy, zaroven je posila vsem klientum, ktere najde
public class SimpleChatServer extends Agent {

    @Override
    protected void setup() {
        super.setup();

        //popis sluzby messaging-server
        ServiceDescription sd = new ServiceDescription();
        sd.setType("messaging-server");
        sd.setName("server");

        //popis tohoto agenta a sluzeb, ktere nabizi
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(this.getAID());
        dfd.addServices(sd);

        //zaregistrovani s DF
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        //pridani chovani, ktere se stara o prijem, vypis a preposlani zprav
        this.addBehaviour(new MessageReceivingBehaviour());
    }

    @Override
    protected void takeDown() {
        super.takeDown();

        //nezapomenout se nakonec odregistrovat z DF
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }


    //chovani, ktere vypisuje prijate zpravy a posila je vsem klientum
    class MessageReceivingBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg == null) {
                block();
                return;
            }

            //vypis prijate zpravy
            System.out.println("[" + new Date().toString() + "] " + msg.getSender().getName() + ": " + msg.getContent());

            //vytvoreni popisu sluzby messaging-client
            ServiceDescription sd = new ServiceDescription();
            sd.setType("messaging-client");

            //vytvoreni popisu agenta, ktery poskytuje messaging-client
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.addServices(sd);

            try {

                //vyhledani klientu
                DFAgentDescription[] clients = DFService.search(myAgent, dfd);

                //vytvoreni zpravy pro klienty
                ACLMessage toClients = new ACLMessage(ACLMessage.INFORM);
                toClients.setContent(msg.getSender().getName() + ": " + msg.getContent());

                //pridani vsech klientu mezi prijemce
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
