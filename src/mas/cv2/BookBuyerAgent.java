/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
 *****************************************************************/

package mas.cv2;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.domain.df;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.proto.AchieveREInitiator;
import jade.proto.ContractNetInitiator;

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.Vector;

public class BookBuyerAgent extends Agent {
	// The list of known seller agents
	private AID[] sellerAgents;

    private Random rnd = new Random();

	// Put agent initializations here
	protected void setup() {
		// Printout a welcome message
		System.out.println("Hello! Buyer-agent "+getAID().getName()+" is ready.");

        // seznam prodavajicih agentu
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("book-selling");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            System.out.println("Found the following seller agents:");
            sellerAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                sellerAgents[i] = result[i].getName();
                System.out.println(sellerAgents[i].getName());
            }
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        //zeptat se vsech agentu na knihy, ktere nabizeji k prodeji
        if (sellerAgents.length > 0) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            for (AID a : sellerAgents) {
                msg.addReceiver(a);
            }
            msg.setContent("get-books-list");
            addBehaviour(new ListBooks(this, msg));
        }

        //zeptat se vsech prodvajicich na cenu knihy LOTR a koupit od nejlevnejsiho
        if (sellerAgents.length > 0) {
            ACLMessage msg = new ACLMessage(ACLMessage.CFP);
            for (AID a : sellerAgents) {
                msg.addReceiver(a);
            }
            msg.setContent("cfp-book|LOTR");
            Date t = new Date();
            msg.setReplyByDate(new Date(t.getTime() + 10000)); //agenti maji na odpoved 10 sekund
            addBehaviour(new BuyBook(this, msg));
        }

	}

    //chovani, ktere se postara o zjisteni seznamu knih prodavanych danym agentem/agenty
    private class ListBooks extends AchieveREInitiator {

        public ListBooks(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        //tady zjistime, kteri agenti nam slibili poslat seznam knih, zprava agree je nepovinna, takze je mozne, ze se tahle
        //metoda vubec nezavola (v tomhle pripade ji prodavajici vzdy posila, takze se zavola}
        @Override
        protected void handleAgree(ACLMessage agree) {
            System.out.println("Agent " + agree.getSender() + " agreed to send the list of books");
        }

        //tady se dozvime, ze nektery z agentu nam nechce seznam knih poslat
        @Override
        protected void handleRefuse(ACLMessage refuse) {
            System.out.println("Agent " + refuse.getSender() + " refused to send the list of books");
        }

        //tady muzeme zpracovat jednotlive odpovedi -- seznamy knih od jednotlivych prodavajicich
        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println("Agent " + inform.getSender() + " List of books: " + inform.getContent());
        }

        //tahle metoda se zavola, kdyz dostaneme vysledky (seznamy knih) od vsech agentu, asi je to dobre misto na vybrani
        //si jedne knihy a naprogramovani jejiho nakupu od prodavajiciho
        @Override
        protected void handleAllResultNotifications(Vector resultNotifications) {
            System.out.println("All replies received");
        }
    }

    //chovani, ktere se napred vsech agentu zepta na cenu knihy a nakonec ji koupi od toho, kdo ji prodava nejlevneji
    private class BuyBook extends ContractNetInitiator {

        public BuyBook(Agent a, ACLMessage cfp) {
            super(a, cfp);
        }

        //tady muzeme jednotlive zpracovavat nabidky
        @Override
        protected void handlePropose(ACLMessage propose, Vector acceptances) {
            System.out.println("Agent: " + propose.getSender().getName() + " proposed " + propose.getContent());
        }

        //tohle je posledni krok, vybrani agenti nam oznamuji, ze dokoncili pozadavek, ktery jsme na ne meli a posilaji
        //vysledek -- to, ze nam prodali knihu
        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println("Agent: " + inform.getSender().getName() + " informed " + inform.getContent());
        }

        //tady muzeme zpracovat vsechny nabidky najednou
        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            System.out.println("Got all responses");

            //najdeme nejlepsi cenu
            int bestPrice = Integer.MAX_VALUE;
            ACLMessage bestResponse = null;
            for (int i = 0; i < responses.size(); i++) {
                ACLMessage response = (ACLMessage)responses.get(i);
                if (response.getPerformative() == ACLMessage.PROPOSE) {
                    int price = Integer.parseInt(response.getContent());
                    if (price < bestPrice) {
                        bestResponse = response;
                        bestPrice = price;
                    }
                }
            }

            //prijmeme nabidku pouze od agenta, ktery nabidl nejlepsi cenu, ostatnim posleme odmitnuti
            for (int i = 0; i < responses.size(); i++) {
                ACLMessage response = (ACLMessage)responses.get(i);
                ACLMessage reply = response.createReply();
                if (response == bestResponse) {
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                } else {
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                }
                acceptances.add(reply);
            }

        }
    }

	// Put agent clean-up operations here
	protected void takeDown() {
        // Printout a dismissal message
		System.out.println("Buyer-agent "+ getAID().getName()+" terminating.");
	}

}
