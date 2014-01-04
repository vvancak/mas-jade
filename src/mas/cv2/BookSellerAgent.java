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
import sun.plugin2.message.Message;

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

        //chovani, ktere se stara o zpracovani pozadavku na seznam knih
        addBehaviour(new ListAvailableBooks(this, new MessageTemplate(new MatchStart("get-books-list"))));

        //chovani, ktere se stara o prodej jedne knihy
        addBehaviour(new SellBook(this, new MessageTemplate(new MatchStart("sell-book"))));

        //chovani, ktere se stara o vraceni nabidky na knihu a pripadny prodej knihy
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

    //MessageTemplate, ktery matchuje zpravy, ktere zacinaji danym retezcem -- hodi se pro odliseni ruznych pozadavku
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

    //chovani, ktere se stara o prodej dane knihy
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
            reply.setContent("sold " + fields[1]);  //tomuhle dneska rikame prodej, predstavte si, ze tu knihu
                                                    // mate, ani platit jste nemuseli :)
            return reply;
        }
    }


    //trida, ktera se stara o vyrizeni pozadavku na seznam knih
    private class ListAvailableBooks extends AchieveREResponder {

        public ListAvailableBooks(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        //pozadavek na seznam knih, bud muzeme vratit rovnou INFORM se senznamem, nebo jen slibime, ze seznam posleme
        //(AGREE, jako zde)
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

        //tady pripravime zpravu se seznamem knih a posleme ji, parametr response je nase AGREE zprava
        @Override
        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException {

            StringBuilder books = new StringBuilder();

            //knihy oddelime "|", aby se to hure matchovalo pomoci regexpu ;)
            for (Object book: catalogue.keySet()) {
                books.append(book.toString() + "|");
            }

            ACLMessage reply = request.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(books.toString());

            return reply;
        }
    }

    //implementace contract-net respondera, na dotaz posle nabidku ceny, za kterou knihu proda a ceka
    //na rozhodnuti initiatora
    private class OfferBookPrices extends ContractNetResponder {

        public OfferBookPrices(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        //zpracovani dotazu na cenu knihy
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

            //kdyz knihu mame v katalogu, nabidneme nasi cenu
            if (catalogue.containsKey(bookTitle)) {
                ACLMessage reply = cfp.createReply();
                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(catalogue.get(bookTitle).toString());
                return reply;
            }

            throw new RefuseException("book not available");
        }

        //agent prijal nasi nabidku, ted musime knihu prodat (pokud ji jeste mame)
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

        //agent nasi nabidku neprijal
        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            System.out.println("Agent " + cfp.getSender() + " rejected the proposal :(");
        }
    }

}
