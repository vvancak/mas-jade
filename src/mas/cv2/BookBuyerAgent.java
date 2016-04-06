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

        // list of book selling agents
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

        //ask all agents about the books they offer for sale
        if (sellerAgents.length > 0) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            for (AID a : sellerAgents) {
                msg.addReceiver(a);
            }
            msg.setContent("get-books-list");
            addBehaviour(new ListBooks(this, msg));
        }

        //ask everyone for the price of LOTR and buy it from the cheapest one
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

    //a behavior which obtains the list of books sold by an agent or a set of agents
    private class ListBooks extends AchieveREInitiator {

        public ListBooks(Agent a, ACLMessage msg) {
            super(a, msg);
        }

        //here, we will find out, who promised to send the list of books, the AGREE message is optional, so it is
        //possible this method will not be called (in our case, the seller send the message, so it will be called)
        @Override
        protected void handleAgree(ACLMessage agree) {
            System.out.println("Agent " + agree.getSender() + " agreed to send the list of books");
        }

        //this method is called if an agent refuses to send the list of books
        @Override
        protected void handleRefuse(ACLMessage refuse) {
            System.out.println("Agent " + refuse.getSender() + " refused to send the list of books");
        }

        //here, we can process the lists of books from the responders
        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println("Agent " + inform.getSender() + " List of books: " + inform.getContent());
        }

        //this method is called after we receive the messages from all reponders, it has a vector of all the messages
        //as a paramter again; this is a good place to select one book and buy it from a seller
        @Override
        protected void handleAllResultNotifications(Vector resultNotifications) {
            System.out.println("All replies received");
        }
    }

    //this behavior asks the seller agents for a book and buys it from the cheapest one
    private class BuyBook extends ContractNetInitiator {

        public BuyBook(Agent a, ACLMessage cfp) {
            super(a, cfp);
        }

        //here, we can process each proposal
        @Override
        protected void handlePropose(ACLMessage propose, Vector acceptances) {
            System.out.println("Agent: " + propose.getSender().getName() + " proposed " + propose.getContent());
        }

        //this is the last step, the agents inform us they finished the action - sold us the book
        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println("Agent: " + inform.getSender().getName() + " informed " + inform.getContent());
        }

        //here, we process all the proposals at once
        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            System.out.println("Got all responses");

            //find the cheapest price
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

            //we accept the proposal from the cheapest agent and reject all other proposals
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
