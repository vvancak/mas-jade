package mas.cv4;

import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.UngroundedException;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.domain.FIPAService;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.*;
import mas.cv4.onto.*;

import java.awt.print.Book;
import java.util.*;

public class qwertyuiop_iii extends Agent {

    Codec codec = new SLCodec();
    Ontology onto = BookOntology.getInstance();

    Random rnd = new Random();

    //Time after which book prices are updated
    static int ticks = 0;
    static final int TICK_PERIOD = 500;
    static final int TOTAL_TIME = 3000 * 60 * 3;
    static final int TOTAL_TICKS = TOTAL_TIME / TICK_PERIOD;

    static final double MIN_SELL_QUOT = 1.1;
    static final double MAX_SELL_QUOT = 1.5;

    static final double TRASH_GAME_PHASE = 0.5 * TOTAL_TICKS;
    static final double TRASH_PROB = 0.5;

    ArrayList<BookInfo> myBooks;
    ArrayList<String> myBookNames;
    ArrayList<Goal> myGoal;

    double myMoney;


    private double getAverageGoalBookValue() {
        double sum = 0;
        for (Goal g : myGoal) sum += g.getValue();
        return (sum / myGoal.size());
    }

    private double getBookValue(BookInfo book) {
        for (Goal g : myGoal) {
            if (g.getBook().getBookID() == book.getBookID()) {
                return g.getValue();
            }
        }

        double decayRate = getAverageGoalBookValue() / TOTAL_TICKS;
        double decayedBookValue = getAverageGoalBookValue() - decayRate;
        return decayedBookValue > 0 ? decayedBookValue : 1;
    }

    private double getBooksValue(ArrayList<BookInfo> books) {
        double value = 0;
        for (BookInfo bi : books) value += getBookValue(bi);
        return value;
    }

    private double getOfferValue(Offer offer) {
        return offer.getMoney() + getBooksValue(offer.getBooks());
    }

    // Willing to sell books for (actual_value) * ( 1 + random number between minimum and maximum added value)
    private double getSellOfferValue(Offer offer) {
        double randomAdd = rnd.nextDouble() * (MAX_SELL_QUOT - MIN_SELL_QUOT);
        return getOfferValue(offer) * (MIN_SELL_QUOT + randomAdd);
    }

    // Method creating an offer. The books opponent wants are given as a parameter
    private ArrayList<Offer> createOffers(ArrayList<BookInfo> books) {
        ArrayList<Offer> offers = new ArrayList<Offer>();

        ArrayList<BookInfo> offerBooks = new ArrayList<>();
        for (BookInfo requestedBook : books) {
            if (!myBookNames.contains(requestedBook.getBookName())) continue;

            // Add requested book
            offerBooks.add(requestedBook);
        }

        if (ticks > TRASH_GAME_PHASE) {
            while (rnd.nextDouble() < TRASH_PROB) {
                int index = rnd.nextInt(myBooks.size());
                BookInfo bi = myBooks.get(index);
                if (!offerBooks.contains(bi)) offerBooks.add(bi);
            }
        }

        Offer o = new Offer();
        o.setBooks(offerBooks);
        o.setMoney(getSellOfferValue(o));
        offers.add(o);

        return offers;
    }

    //map book ids to goals, index in array represents goal
    private void update(AgentInfo ai) {
        myBooks = ai.getBooks();
        myGoal = ai.getGoals();
        myMoney = ai.getMoney();
        myBookNames = new ArrayList<>();

        for (BookInfo bi : myBooks) myBookNames.add(bi.getBookName());
    }

    private void LogState() {
        if (true) return;
        System.out.printf("%50s:\n", this.getName());
        System.out.println(this.myBooks);
        System.out.println(this.myGoal);
        System.out.println(this.myMoney);
    }

    //We just bought these books for that price.
    private void boughtBooks(ArrayList<BookInfo> books, double price) {
        System.out.printf("BUY: %50s bought books for %13f \n", this.getName(), price);
    }

    //We just sold these books for that price.
    private void soldBooks(ArrayList<BookInfo> books, double price) {
        System.out.printf("SELL: %50s sold books for %13f \n", this.getName(), price);
    }

    @Override
    protected void setup() {
        super.setup();

        //register the codec and the ontology with the content manager
        this.getContentManager().registerLanguage(codec);
        this.getContentManager().registerOntology(onto);

        //book-trader service description
        ServiceDescription sd = new ServiceDescription();
        sd.setType("book-trader");
        sd.setName("qwertyuiop_iii");

        //description of this agent and the services it provides
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(this.getAID());
        dfd.addServices(sd);

        //register to DF
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        //add behavior which waits for the StartTrading message
        addBehaviour(new StartTradingBehaviour(this, MessageTemplate.MatchPerformative(ACLMessage.REQUEST)));
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

    // waits for the StartTrading message and adds the trading behavior
    class StartTradingBehaviour extends AchieveREResponder {


        public StartTradingBehaviour(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage handleRequest(ACLMessage request) throws NotUnderstoodException, RefuseException {

            try {
                ContentElement ce = getContentManager().extractContent(request);

                if (!(ce instanceof Action)) {
                    throw new NotUnderstoodException("");
                }
                Action a = (Action) ce;


                //we got the request to start trading
                if (a.getAction() instanceof StartTrading) {

                    //find out what our goals are
                    ACLMessage getMyInfo = new ACLMessage(ACLMessage.REQUEST);
                    getMyInfo.setLanguage(codec.getName());
                    getMyInfo.setOntology(onto.getName());

                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("environment");
                    DFAgentDescription dfd = new DFAgentDescription();
                    dfd.addServices(sd);

                    DFAgentDescription[] envs = DFService.search(myAgent, dfd);

                    getMyInfo.addReceiver(envs[0].getName());
                    getContentManager().fillContent(getMyInfo, new Action(envs[0].getName(), new GetMyInfo()));

                    ACLMessage myInfo = FIPAService.doFipaRequestClient(myAgent, getMyInfo);

                    Result res = (Result) getContentManager().extractContent(myInfo);

                    AgentInfo ai = (AgentInfo) res.getValue();

                    update(ai);

                    //add a behavior which tries to buy a book every two seconds
                    addBehaviour(new TradingBehaviour(myAgent, 2000));

                    //add a behavior which sells book to other agents
                    addBehaviour(new SellBook(myAgent, MessageTemplate.MatchPerformative(ACLMessage.CFP)));

                    //add a behavior which updates the prices
                    addBehaviour(new UpdatePrices(myAgent));

                    //reply that we are able to start trading (the message is ignored by the environment)
                    ACLMessage reply = request.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    return reply;
                }

                throw new NotUnderstoodException("");

            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            } catch (FIPAException e) {
                e.printStackTrace();
            }

            return super.handleRequest(request);
        }


        //this behavior updates the prices of books we are looking for or those we are selling
        class UpdatePrices extends TickerBehaviour {

            public UpdatePrices(Agent a) {
                super(a, TICK_PERIOD);
            }

            @Override
            protected void onTick() {
                try {

                    ++ticks;
                    LogState();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }


        //this behavior trades with books
        class TradingBehaviour extends TickerBehaviour {


            public TradingBehaviour(Agent a, long period) {
                super(a, period);
            }

            @Override
            protected void onTick() {

                try {
                    //find other seller and prepare a CFP
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("book-trader");
                    DFAgentDescription dfd = new DFAgentDescription();
                    dfd.addServices(sd);

                    DFAgentDescription[] traders = DFService.search(myAgent, dfd);

                    ACLMessage buyBook = new ACLMessage(ACLMessage.CFP);
                    buyBook.setLanguage(codec.getName());
                    buyBook.setOntology(onto.getName());
                    buyBook.setReplyByDate(new Date(System.currentTimeMillis() + 5000));

                    for (DFAgentDescription dfad : traders) {
                        if (dfad.getName().equals(myAgent.getAID()))
                            continue;
                        buyBook.addReceiver(dfad.getName());
                    }

                    ArrayList<BookInfo> bis = new ArrayList<BookInfo>();

                    //choose a book from goals to buy
                    BookInfo bi = new BookInfo();
                    bi.setBookName(myGoal.get(rnd.nextInt(myGoal.size())).getBook().getBookName());
                    bis.add(bi);

                    SellMeBooks smb = new SellMeBooks();
                    smb.setBooks(bis);

                    getContentManager().fillContent(buyBook, new Action(myAgent.getAID(), smb));
                    addBehaviour(new ObtainBook(myAgent, buyBook));
                } catch (Codec.CodecException e) {
                    e.printStackTrace();
                } catch (OntologyException e) {
                    e.printStackTrace();
                } catch (FIPAException e) {
                    e.printStackTrace();
                }

            }
        }


        //this behavior takes care of the buying of the book itself
        class ObtainBook extends ContractNetInitiator {

            public ObtainBook(Agent a, ACLMessage cfp) {
                super(a, cfp);
            }

            Chosen c;  //we need to remember what offer we have chosen
            ArrayList<BookInfo> shouldReceive; //we also remember what the seller offered to us


            //the seller informs us it processed the order, we need to send the payment
            @Override
            protected void handleInform(ACLMessage inform) {
                try {


                    //create the transaction info and send it to the environment
                    MakeTransaction mt = new MakeTransaction();

                    mt.setSenderName(myAgent.getName());
                    mt.setReceiverName(inform.getSender().getName());
                    mt.setTradeConversationID(inform.getConversationId());

                    if (c.getOffer().getBooks() == null)
                        c.getOffer().setBooks(new ArrayList<BookInfo>());

                    mt.setSendingBooks(c.getOffer().getBooks());
                    mt.setSendingMoney(c.getOffer().getMoney());

                    if (shouldReceive == null)
                        shouldReceive = new ArrayList<BookInfo>();

                    mt.setReceivingBooks(shouldReceive);
                    mt.setReceivingMoney(0.0);

                    //Cannot actually grasp the concept with c.getOffer().getBooks() vs shouldRecieve
                    //Those should be the same... But...

                    //We managed to bought these books for that price
                    boughtBooks(c.getOffer().getBooks(), c.getOffer().getMoney());


                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("environment");
                    DFAgentDescription dfd = new DFAgentDescription();
                    dfd.addServices(sd);

                    DFAgentDescription[] envs = DFService.search(myAgent, dfd);

                    ACLMessage transReq = new ACLMessage(ACLMessage.REQUEST);
                    transReq.addReceiver(envs[0].getName());
                    transReq.setLanguage(codec.getName());
                    transReq.setOntology(onto.getName());
                    transReq.setReplyByDate(new Date(System.currentTimeMillis() + 5000));

                    getContentManager().fillContent(transReq, new Action(envs[0].getName(), mt));
                    addBehaviour(new SendBook(myAgent, transReq));

                } catch (UngroundedException e) {
                    e.printStackTrace();
                } catch (OntologyException e) {
                    e.printStackTrace();
                } catch (Codec.CodecException e) {
                    e.printStackTrace();
                } catch (FIPAException e) {
                    e.printStackTrace();
                }

            }

            //process the offers from the sellers
            @Override
            protected void handleAllResponses(Vector responses, Vector acceptances) {
                Iterator it = responses.iterator();

                //we need to accept only one offer, otherwise we create two transactions with the same ID
                boolean accepted = false;
                while (it.hasNext()) {
                    ACLMessage response = (ACLMessage) it.next();

                    ContentElement ce = null;
                    try {
                        if (response.getPerformative() == ACLMessage.REFUSE) {
                            continue;
                        }

                        ce = getContentManager().extractContent(response);

                        ChooseFrom cf = (ChooseFrom) ce;

                        ArrayList<Offer> offers = cf.getOffers();
                        double value = getBooksValue(cf.getWillSell());

                        //find out which offers we can fulfill (we have all requested books and enough money)
                        //do not sel books that are in goal
                        ArrayList<Offer> canFulfill = new ArrayList<Offer>();
                        for (Offer o : offers) {
                            if (o.getMoney() > myMoney) continue;
                            if (o.getMoney() > value) continue;

                            boolean foundAll = true;
                            if (o.getBooks() != null)
                                for (BookInfo bi : o.getBooks()) {
                                    String bn = bi.getBookName();

                                    boolean found = false;
                                    for (int j = 0; j < myBooks.size(); j++) {
                                        if (myBooks.get(j).getBookName().equals(bn)) {
                                            found = true;
                                            bi.setBookID(myBooks.get(j).getBookID());
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        foundAll = false;
                                        break;
                                    }
                                }

                            if (foundAll) {
                                canFulfill.add(o);
                            }
                        }

                        //if none, we REJECT the proposal, we also reject all proposal if we already accepted one
                        if (canFulfill.size() == 0 || accepted) {
                            ACLMessage acc = response.createReply();
                            acc.setPerformative(ACLMessage.REJECT_PROPOSAL);
                            acceptances.add(acc);
                            continue;
                        }

                        ACLMessage acc = response.createReply();
                        acc.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        accepted = true;

                        //choose an offer
                        Chosen ch = new Chosen();
                        canFulfill.sort((o1, o2) -> getOfferValue(o1) < getOfferValue(o2) ? -1 : 1);
                        ch.setOffer(canFulfill.get(0));

                        c = ch;
                        shouldReceive = cf.getWillSell();

                        getContentManager().fillContent(acc, ch);
                        acceptances.add(acc);

                    } catch (Codec.CodecException e) {
                        e.printStackTrace();
                    } catch (OntologyException e) {
                        e.printStackTrace();
                    }

                }

            }
        }


        //this behavior processes the selling of books
        class SellBook extends SSResponderDispatcher {

            public SellBook(Agent a, MessageTemplate tpl) {
                super(a, tpl);
            }

            @Override
            protected Behaviour createResponder(ACLMessage initiationMsg) {
                return new SellBookResponder(myAgent, initiationMsg);
            }
        }

        class SellBookResponder extends SSContractNetResponder {

            public SellBookResponder(Agent a, ACLMessage cfp) {
                super(a, cfp);
            }

            @Override
            protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException {

                try {
                    Action ac = (Action) getContentManager().extractContent(cfp);

                    SellMeBooks smb = (SellMeBooks) ac.getAction();
                    ArrayList<BookInfo> books = smb.getBooks();

                    ChooseFrom cf = new ChooseFrom();
                    ArrayList<Offer> offers = createOffers(books);

                    ArrayList<BookInfo> offerBooks = new ArrayList<>();
                    for (Offer o : offers) {
                        for (BookInfo bi : o.getBooks()) {
                            if (!offerBooks.contains(bi)) offerBooks.add(bi);
                        }
                    }

                    cf.setWillSell(offerBooks);
                    cf.setOffers(offers);

                    //send the offers
                    ACLMessage reply = cfp.createReply();
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setReplyByDate(new Date(System.currentTimeMillis() + 5000));
                    getContentManager().fillContent(reply, cf);

                    return reply;
                } catch (UngroundedException e) {
                    e.printStackTrace();
                } catch (Codec.CodecException e) {
                    e.printStackTrace();
                } catch (OntologyException e) {
                    e.printStackTrace();
                }

                throw new FailureException("");
            }

            //the buyer decided to accept an offer
            @Override
            protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {

                try {
                    ChooseFrom cf = (ChooseFrom) getContentManager().extractContent(propose);

                    //prepare the transaction info and send it to the environment
                    MakeTransaction mt = new MakeTransaction();

                    mt.setSenderName(myAgent.getName());
                    mt.setReceiverName(cfp.getSender().getName());
                    mt.setTradeConversationID(cfp.getConversationId());

                    if (cf.getWillSell() == null) {
                        cf.setWillSell(new ArrayList<BookInfo>());
                    }

                    mt.setSendingBooks(cf.getWillSell());
                    mt.setSendingMoney(0.0);

                    Chosen c = (Chosen) getContentManager().extractContent(accept);

                    if (c.getOffer().getBooks() == null) {
                        c.getOffer().setBooks(new ArrayList<BookInfo>());
                    }

                    mt.setReceivingBooks(c.getOffer().getBooks());
                    mt.setReceivingMoney(c.getOffer().getMoney());

                    //We managed to sold these books for that price
                    soldBooks(c.getOffer().getBooks(), c.getOffer().getMoney());

                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("environment");
                    DFAgentDescription dfd = new DFAgentDescription();
                    dfd.addServices(sd);

                    DFAgentDescription[] envs = DFService.search(myAgent, dfd);

                    ACLMessage transReq = new ACLMessage(ACLMessage.REQUEST);
                    transReq.addReceiver(envs[0].getName());
                    transReq.setLanguage(codec.getName());
                    transReq.setOntology(onto.getName());
                    transReq.setReplyByDate(new Date(System.currentTimeMillis() + 5000));

                    getContentManager().fillContent(transReq, new Action(envs[0].getName(), mt));

                    addBehaviour(new SendBook(myAgent, transReq));

                    ACLMessage reply = accept.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    return reply;

                } catch (UngroundedException e) {
                    e.printStackTrace();
                } catch (OntologyException e) {
                    e.printStackTrace();
                } catch (Codec.CodecException e) {
                    e.printStackTrace();
                } catch (FIPAException e) {
                    e.printStackTrace();
                }

                throw new FailureException("");
            }
        }

        //after the transaction is complete (the environment returned an INFORM), we update our information
        class SendBook extends AchieveREInitiator {

            public SendBook(Agent a, ACLMessage msg) {
                super(a, msg);
            }

            @Override
            protected void handleInform(ACLMessage inform) {

                try {
                    ACLMessage getMyInfo = new ACLMessage(ACLMessage.REQUEST);
                    getMyInfo.setLanguage(codec.getName());
                    getMyInfo.setOntology(onto.getName());

                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("environment");
                    DFAgentDescription dfd = new DFAgentDescription();
                    dfd.addServices(sd);

                    DFAgentDescription[] envs = DFService.search(myAgent, dfd);

                    getMyInfo.addReceiver(envs[0].getName());
                    getContentManager().fillContent(getMyInfo, new Action(envs[0].getName(), new GetMyInfo()));

                    ACLMessage myInfo = FIPAService.doFipaRequestClient(myAgent, getMyInfo);

                    Result res = (Result) getContentManager().extractContent(myInfo);

                    AgentInfo ai = (AgentInfo) res.getValue();

                    update(ai);
                } catch (OntologyException e) {
                    e.printStackTrace();
                } catch (FIPAException e) {
                    e.printStackTrace();
                } catch (Codec.CodecException e) {
                    e.printStackTrace();
                }

            }
        }
    }

}