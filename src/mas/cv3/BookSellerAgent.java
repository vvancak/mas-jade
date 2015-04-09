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
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ReceiverBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.AchieveREResponder;
import jade.proto.ContractNetResponder;
import jade.util.leap.ArrayList;
import mas.cv3.onto.BookInfo;
import mas.cv3.onto.BookOntology;
import mas.cv3.onto.GetBookList;
import mas.cv3.onto.SellBook;

import java.util.Hashtable;
import java.util.Random;

public class BookSellerAgent extends Agent {
	// The catalogue of books for sale (maps the title of a book to its price)
	private Hashtable catalogue;
	// The GUI by means of which the user can add books in the catalogue
	private BookSellerGui myGui;

    private Codec codec = new SLCodec();
    private Ontology onto = BookOntology.getInstance();

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

        //napred je potreba rict agentovi, jakym zpusobem jsou zpravy kodovany, a jakou pouzivame ontologii
        this.getContentManager().registerLanguage(codec);
        this.getContentManager().registerOntology(onto);

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
        addBehaviour(new ListAvailableBooks(this, MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), new MessageTemplate(new MatchListBooks()))));

        //chovani, ktere se stara o prodej jedne knihy
        addBehaviour(new HandleSellBook(this, MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST), new MessageTemplate(new MatchSellBook()))));

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

    //MessageTemplate, ktery matchuje zpravy, ktere obsahuji GetBookList
    private class MatchListBooks implements MessageTemplate.MatchExpression {

        @Override
        public boolean match(ACLMessage aclMessage) {
            System.err.println(aclMessage.getContent());
            ContentElement ce = null;
            try {
                ce = getContentManager().extractContent(aclMessage);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }

            Action a = (Action)ce;
            return a.getAction() instanceof GetBookList;
        }
    }

    //MessageTemplate, ktery matchuje zpravy, ktere obsahuji SellBook
    private class MatchSellBook implements MessageTemplate.MatchExpression {

        @Override
        public boolean match(ACLMessage aclMessage) {
            System.err.println(aclMessage.getContent());
            ContentElement ce = null;
            try {
                ce = getContentManager().extractContent(aclMessage);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }

            Action a = (Action)ce;
            return a.getAction() instanceof SellBook;
        }
    }
    //chovani, ktere se stara o prodej dane knihy
    private class HandleSellBook extends AchieveREResponder {

        public HandleSellBook(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {

            try {
                ContentElement ce = getContentManager().extractContent(request);
                Action act = (Action)ce;

                if (!(act.getAction() instanceof SellBook))
                    throw (new NotUnderstoodException(""));

                SellBook sb = (SellBook)act.getAction();

                if (!catalogue.containsKey(sb.getBi().getName())) {
                    throw new RefuseException("book not available");
                }

                ACLMessage reply = request.createReply();
                reply.setPerformative(ACLMessage.INFORM);

                BookInfo bi = new BookInfo();
                bi.setName(sb.getBi().getName());
                bi.setPrice((Integer)catalogue.get(sb.getBi().getName()));

                getContentManager().fillContent(reply, new Result(sb, bi));

                return reply;
                } catch (Codec.CodecException e) {
                    e.printStackTrace();
                } catch (OntologyException e) {
                    e.printStackTrace();
                }

            throw new NotUnderstoodException("");
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

            ContentElement ce = null;
            try {
                ce = getContentManager().extractContent(request);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
            Action a = (Action)ce;

            if (a.getAction() instanceof GetBookList) {
                ACLMessage reply = request.createReply();
                reply.setPerformative(ACLMessage.AGREE);
                return reply;
            }

            throw new NotUnderstoodException("");

        }

        //tady pripravime zpravu se seznamem knih a posleme ji, parametr response je nase AGREE zprava
        @Override
        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response) throws FailureException {

            try {
                ContentElement ce = getContentManager().extractContent(request);
                Action a = (Action)ce;

                if (a.getAction() instanceof GetBookList) {

                    ArrayList bis = new ArrayList();
                    for (Object key : catalogue.keySet()) {
                        BookInfo bi = new BookInfo();
                        bi.setName(key.toString());
                        bi.setPrice((Integer)catalogue.get(key));
                        bis.add(bi);
                    }

                    ACLMessage reply = request.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    getContentManager().fillContent(reply, new Result(a.getAction(), bis));
                    return reply;
                }
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }

            throw new FailureException("");
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

            try {
                ContentElement ce = getContentManager().extractContent(cfp);
                Action ac = (Action)ce;

                if (ac.getAction() instanceof SellBook) {
                    SellBook sb = (SellBook)ac.getAction();
                    String bookTitle = sb.getBi().getName();

                    //kdyz knihu mame v katalogu, nabidneme nasi cenu
                    if (catalogue.containsKey(bookTitle)) {
                        ACLMessage reply = cfp.createReply();
                        reply.setPerformative(ACLMessage.PROPOSE);
                        BookInfo bi = new BookInfo();
                        bi.setName(bookTitle);
                        bi.setPrice((Integer)catalogue.get(bookTitle));
                        getContentManager().fillContent(reply, new Result(sb, bi));
                        return reply;
                    }

                    throw new RefuseException("book not available");
                }
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }

            throw new NotUnderstoodException("");

        }

        //agent prijal nasi nabidku, ted musime knihu prodat (pokud ji jeste mame)
        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {

            try {
                ContentElement ce = getContentManager().extractContent(cfp);
                Action ac = (Action)ce;
                SellBook sb = (SellBook)ac.getAction();
                String bookTitle = sb.getBi().getName();

                if (!catalogue.containsKey(bookTitle)) {
                    throw new FailureException("book no longer available");
                }

                ACLMessage reply = accept.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                BookInfo bi = new BookInfo();
                bi.setName(sb.getBi().getName());
                bi.setPrice((Integer)catalogue.get(bi.getName()));
                getContentManager().fillContent(reply, new Result(ac.getAction(), bi));
                return reply;

            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }

            throw new FailureException("");
        }

        //agent nasi nabidku neprijal
        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject) {
            System.out.println("Agent " + cfp.getSender() + " rejected the proposal :(");
        }
    }

}
