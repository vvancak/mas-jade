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

package mas.cv3;

import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPAService;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;
import jade.proto.ContractNetInitiator;
import jade.util.leap.Iterator;
import jade.util.leap.List;
import mas.cv3.onto.BookInfo;
import mas.cv3.onto.BookOntology;
import mas.cv3.onto.GetBookList;
import mas.cv3.onto.SellBook;

import javax.swing.*;
import java.util.Date;
import java.util.Random;
import java.util.Vector;

public class BookBuyerAgent extends Agent {
	// The list of known seller agents
	private AID[] sellerAgents;

    private Random rnd = new Random();

    private Codec codec = new SLCodec();
    private Ontology onto = BookOntology.getInstance();

	// Put agent initializations here
	protected void setup() {
		// Printout a welcome message
		System.out.println("Hello! Buyer-agent "+getAID().getName()+" is ready.");

        //register the coded and the ontology with the content manager
        this.getContentManager().registerLanguage(codec);
        this.getContentManager().registerOntology(onto);

        // get the list of seller agents
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

        //ask all seller for the list of books they sell
        if (sellerAgents.length > 0) {
            addBehaviour(new ListBooks(this, null, sellerAgents));
        }

        //ask everyone about for the price of LOTR and buy it from the cheapest
        if (sellerAgents.length > 0) {
            addBehaviour(new BuyBook(this, null, sellerAgents));
        }

	}

    //this behavior obtains the list of books sold by the sellers
    private class ListBooks extends AchieveREInitiator {

        AID[] sellers;

        public ListBooks(Agent a, ACLMessage msg, AID[] sellers) {
            super(a, msg);
            this.sellers = sellers;
        }

        @Override
        protected Vector prepareRequests(ACLMessage request) {

            Vector requests = new Vector();

            try {
                for (AID seller : sellers) {
                    ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                    msg.addReceiver(seller);
                    GetBookList gbl = new GetBookList();
                    msg.setOntology(onto.getName());
                    msg.setLanguage(codec.getName());
                    getContentManager().fillContent(msg, new Action(seller, gbl));
                    requests.add(msg);
                }
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }

            return requests;
        }

        //here, we can handle the AGREE message, it is optional, so this method may not be called at all
        @Override
        protected void handleAgree(ACLMessage agree) {
            System.out.println("Agent " + agree.getSender() + " agreed to send the list of books");
        }

        //handle the REFUSE message if and agents refuses to send the list of books
        @Override
        protected void handleRefuse(ACLMessage refuse) {
            System.out.println("Agent " + refuse.getSender() + " refused to send the list of books");
        }

        //process the answers -- list of books from individual agents
        @Override
        protected void handleInform(ACLMessage inform) {

            ContentElement ce = null;
            try {
                ce = getContentManager().extractContent(inform);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
            Result r = (Result)ce;
            List books = r.getItems();
            System.out.print("Agent " + inform.getSender() + " List of books: ");
            Iterator it = books.iterator();
            while (it.hasNext()) {
                BookInfo bi = (BookInfo)it.next();
                System.out.print("("+bi.getName()+","+bi.getPrice()+") ");
            }
            System.out.println();
        }

        //this method is called when we receive all responses from the sellers, it may be a good place to select
        // the cheapest seller and buy the from the agent
        @Override
        protected void handleAllResultNotifications(Vector resultNotifications) {
            System.out.println("All replies received");
        }
    }

    //this behavior first asks all the agents for the price of a book and buys it from the cheapest one
    private class BuyBook extends ContractNetInitiator {

        AID[] sellers;

        public BuyBook(Agent a, ACLMessage cfp, AID[] sellers) {
            super(a, cfp);
            this.sellers = sellers;
        }

        @Override
        protected Vector prepareCfps(ACLMessage cfp) {

            Vector cfps = new Vector();

            try {
                for (AID seller: sellers) {
                    ACLMessage msg = new ACLMessage(ACLMessage.CFP);
                    msg.addReceiver(seller);
                    BookInfo bi = new BookInfo();
                    bi.setName("LOTR");
                    SellBook sb = new SellBook();
                    sb.setBi(bi);
                    msg.setOntology(onto.getName());
                    msg.setLanguage(codec.getName());
                    getContentManager().fillContent(msg, new Action(seller, sb));
                    Date t = new Date();
                    msg.setReplyByDate(new Date(t.getTime() + 10000)); //agents must reply in 10 seconds
                    cfps.add(msg);
                }
            }
             catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }

            return cfps;
        }

        //process individual proposals
        @Override
        protected void handlePropose(ACLMessage propose, Vector acceptances) {
            System.out.println("Agent: " + propose.getSender().getName() + " proposed " + propose.getContent());
        }

        //this is the last step - it processes the inform that the book is sold to us
        @Override
        protected void handleInform(ACLMessage inform) {
            System.out.println("Agent: " + inform.getSender().getName() + " informed " + inform.getContent());
        }

        //process all the proposals at once
        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances) {
            System.out.println("Got all responses");

            //find the best price
            int bestPrice = Integer.MAX_VALUE;
            ACLMessage bestResponse = null;
            for (int i = 0; i < responses.size(); i++) {
                ACLMessage response = (ACLMessage)responses.get(i);
                if (response.getPerformative() == ACLMessage.PROPOSE) {
                    ContentElement ce = null;
                    try {
                        ce = getContentManager().extractContent(response);
                    } catch (Codec.CodecException e) {
                        e.printStackTrace();
                    } catch (OntologyException e) {
                        e.printStackTrace();
                    }
                    Result r = (Result)ce;

                    BookInfo bi = (BookInfo)r.getValue();
                    int price = bi.getPrice();
                    if (price < bestPrice) {
                        bestResponse = response;
                        bestPrice = price;
                    }
                }
            }

            //accept the offer from the cheapest agent, send reject to the rest of agents
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
