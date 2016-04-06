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
import jade.core.behaviours.*;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPANames;
import jade.domain.FIPAService;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.proto.*;

import java.util.*;

public class BookSellerAgent extends Agent {
	// The catalogue of books for sale (maps the title of a book to its price)
	private Hashtable catalogue;
	// The GUI by means of which the user can add books in the catalogue
	private BookSellerGui myGui;

    private Random rnd = new Random();

	// Put agent initializations here
	protected void setup() {
		// Create the catalogue
		catalogue = new Hashtable();
        catalogue.put("LOTR", rnd.nextInt(100) + 50);
        catalogue.put("Hobbit", rnd.nextInt(50) + 40);

		// Create and show the GUI 
		myGui = new BookSellerGui(this);
		myGui.showGui();

		// Register the book-selling service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-selling");
		sd.setName("JADE-book-trading");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

        //behavior which processes the requests for the lists of books
        addBehaviour(new ListAvailableBooks(this, new MessageTemplate(new MatchStart("get-books-list"))));

        //behavior which sells a book
        addBehaviour(new SellBook(this, new MessageTemplate(new MatchStart("sell-book"))));

        //behavior which proposes a price for a book and sells the book if the proposal is accepted
        addBehaviour(new OfferBookPrices(this, MessageTemplate.MatchPerformative(ACLMessage.CFP)));

	}

    /**
     This is invoked by the GUI when the user adds a new book for sale
     */
    public void updateCatalogue(final String title, final int price) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                catalogue.put(title, new Integer(price));
                System.out.println(title+" inserted into catalogue. Price = "+price);
            }
        } );
    }

    // Put agent clean-up operations here
	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Close the GUI
		myGui.dispose();
		// Printout a dismissal message
		System.out.println("Seller-agent "+getAID().getName()+" terminating.");
	}

    //MessageTemplate, which matches messages starting with a given string
    private class MatchStart implements MessageTemplate.MatchExpression {

        String start;

        public MatchStart(String start) {
            this.start = start;
        }

        @Override
        public boolean match(ACLMessage aclMessage) {
            if (aclMessage.getContent() == null)
                return false;
            return aclMessage.getContent().startsWith(start);
        }
    }

    //behavior which sells a single book
    private class SellBook extends AchieveREResponder {

        public SellBook(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {

            String content = request.getContent();

            String fields[] = content.split("\\|");
            if (fields.length != 2) {
                throw new NotUnderstoodException("invalid request format");
            }

            if (!catalogue.containsKey(fields[1])) {
                throw new RefuseException("book not available");
            }

            ACLMessage reply = request.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent("sold " + fields[1]);  //this is what we call "sell" today, imagine you have to book now :)
            return reply;
        }
    }


    //behavior which processes the request for the lists of books
    private class ListAvailableBooks extends AchieveREResponder {

        public ListAvailableBooks(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        //request for list of books, we can either returs the list with an INFORM or promise to return it later with
        //an AGREE, like here//
        @Override
        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {

            String content = request.getContent();
            if (!content.equals("get-books-list")) {
                throw new NotUnderstoodException("unknown request");
            }

            ACLMessage reply = request.createReply();
            reply.setPerformative(ACLMessage.AGREE);

            return reply;
        }

        //here we prepare the message and send it, the 'response' parameter is our AGREE message
        @Override
        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException {

            StringBuilder books = new StringBuilder();

            //books are delimited by "|"
            for (Object book: catalogue.keySet()) {
                books.append(book.toString() + "|");
            }

            ACLMessage reply = request.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(books.toString());

            return reply;
        }
    }

    //implementation of the contract-net responder, it waits for a CFP, proposes a price and then waits for the decision
    //of the initiator
    private class OfferBookPrices extends ContractNetResponder {

        public OfferBookPrices(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        //process the request for the price of books
        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {

            String content = cfp.getContent();

            if (!content.startsWith("cfp-book")) {
                throw new NotUnderstoodException("unknown cfp");
            }

            String[] fields = content.split("\\|");
            if (fields.length != 2) {
                throw new NotUnderstoodException("invalid cfp format");
            }

            String bookTitle = fields[1];

            //if we have the book, we offer our price
            if (catalogue.containsKey(bookTitle)) {
                ACLMessage reply = cfp.createReply();
                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(catalogue.get(bookTitle).toString());
                return reply;
            }

            throw new RefuseException("book not available");
        }

        //the agent accepted our request, we have to sell the book now (if we still have it)
        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {

            String content = cfp.getContent();
            String[] fields = content.split("\\|");
            String bookTitle = fields[1];

            if (!catalogue.containsKey(bookTitle)) {
                throw new FailureException("book no longer available");
            }

            ACLMessage reply = accept.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent("sold " + bookTitle);

            return reply;
        }

        //the initiator rejected our proposal
        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            System.out.println("Agent " + cfp.getSender() + " rejected the proposal :(");
        }
    }

}
