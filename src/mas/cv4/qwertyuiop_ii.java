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

import java.util.*;

public class qwertyuiop_ii extends Agent {

    Codec codec = new SLCodec();
    Ontology onto = BookOntology.getInstance();

    Random rnd = new Random();

    static final double MIN_SELL_QUOT = 0.5;
    static final double MAX_SELL_QUOT = 1.0;
    static final double MIN_BUY_QUOT = 1.1;
    static final double MAX_BUY_QUOT = 1.5;
    static final double TRASH_PER_REQUESTED_BOOK = 0.3;

    //Time after which book prices are updated
    static int ticks = 0;
    static final int TICK_PERIOD = 2000;
    static final int TOTAL_TIME = 3000 * 60 * 3;
    static final int totalTicks = TOTAL_TIME / TICK_PERIOD;

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

        double decayRate = getAverageGoalBookValue() / totalTicks;
        double decayedBookValue = getAverageGoalBookValue() - decayRate;
        return decayedBookValue > 0 ? decayedBookValue : 1;
    }

    private double getBooksValue(ArrayList<BookInfo> books) {
        double price = 0;
        if (books != null) {
            for (BookInfo book : books) {
                price += getBookValue(book);
            }
        }
        return price;
    }

    // Willing to sell books for (actual_value) * ( 1 + random number between minimum and maximum added value)
    private double getSellBooksValue(ArrayList<BookInfo> books) {
        double randomAdd = rnd.nextDouble() * (MAX_SELL_QUOT - MIN_SELL_QUOT);
        return getBooksValue(books) * (MIN_SELL_QUOT + randomAdd);
    }

    // Method creating an offer. The books opponent wants are given as a parameter
    //the offer is what we are willing to give - all
    private ArrayList<Offer> createOffers(ArrayList<BookInfo> books) {
        ArrayList<Offer> offers = new ArrayList<Offer>();

        double randomAdd = rnd.nextDouble() * (MAX_SELL_QUOT - MIN_SELL_QUOT);
        double price = getBooksValue(books) * (MIN_SELL_QUOT + randomAdd);

        Offer o = new Offer();
        o.setMoney(price);
        offers.add(o);

        while(rnd.nextDouble() < 0.3)
        {
            ArrayList<BookInfo> randomBooks = new ArrayList<BookInfo>();

            for(int j = 0; j < rnd.nextInt(myBooks.size()); j++)
                randomBooks.add(myBooks.get(rnd.nextInt(myBooks.size())));

            o = new Offer();
            o.setBooks(randomBooks);
            double value = getSellBooksValue(randomBooks);
            o.setMoney(price - value > 0 ? price - value : 0);
            offers.add(o);
        }

        return offers;
    }

    //Prepares a list of books which we want to buy
    private ArrayList<BookInfo> putOutAd()
    {
        ArrayList <BookInfo> books = new ArrayList<BookInfo>();

        //choose a book from goals to buy
        BookInfo bi = new BookInfo();
        bi.setBookName(myGoal.get(rnd.nextInt(myGoal.size())).getBook().getBookName());
        books.add(bi);

        return books;
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
        //System.out.printf("%50s:\n", this.getName());
        //System.out.print(this.getName()); System.out.println(this.myBooks);
        //System.out.print(this.getName()); System.out.println(this.myGoal);
        //System.out.print(this.getName()); System.out.println(this.myMoney);
    }

    //We just bought these books for that price.
    private void boughtBooks(ArrayList<BookInfo> recieve, ArrayList<BookInfo> priceBooks, double price)
    {
        //System.out.printf("BUY: %s recieved %f" + (priceBooks.size() > 0 ?  "and " : ""), this.getName(), price);System.out.println(priceBooks);
        //System.out.printf("BUY: in exchange for "); System.out.println(recieve);
    }

    //We just sold these books for that price.
    private void soldBooks(ArrayList<BookInfo> recieve, ArrayList<BookInfo> priceBooks, double price)
    {
        //System.out.printf("BUY: %s gave %f" + (priceBooks.size() > 0 ?  "and " : ""), this.getName(), price);System.out.println(priceBooks);
        //System.out.printf("SELL: in exchange for "); System.out.println(recieve);
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
        sd.setName("qwertyuiop_ii");

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

                    //ticker
                    addBehaviour(new MainTickerBehaviour(myAgent));

                    //add a behavior which tries to buy a book every two seconds
                    addBehaviour(new TradingBehaviour(myAgent));

                    //add a behavior which sells book to other agents
                    addBehaviour(new SellBook(myAgent, MessageTemplate.MatchPerformative(ACLMessage.CFP)));

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
        class MainTickerBehaviour extends TickerBehaviour {

            public MainTickerBehaviour(Agent a) {
                super(a, TICK_PERIOD);
            }

            @Override
            protected void onTick() {
                LogState();
                ticks++;
            }
        }

        //this behavior trades with books
        class TradingBehaviour extends TickerBehaviour {


            public TradingBehaviour(Agent a) {
                super(a, TICK_PERIOD);
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

                    ArrayList<BookInfo> bis = putOutAd();

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

                    boughtBooks(shouldReceive, c.getOffer().getBooks(), c.getOffer().getMoney());

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
                        double value = getBooksValue(cf.getWillSell());

                        ArrayList<Offer> offers = cf.getOffers();
                        if (offers == null) continue;

                        //find out which offers we can fulfill (we have all requested books and enough money)
                        ArrayList<Offer> worthIt = new ArrayList<Offer>();
                        for (Offer o: offers) {
                            if (o == null) continue;

                            // not enough money
                            if (o.getMoney() > myMoney) continue;

                            //not worth it
                            if (o.getMoney() + getBooksValue(o.getBooks()) > value) continue;

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
                                worthIt.add(o);
                            }
                        }

                        worthIt.sort((o1, o2) -> getBooksValue(o1.getBooks())+o1.getMoney() < getBooksValue(o2.getBooks())+o2.getMoney() ? -1 : 1);

                        //if none, we REJECT the proposal, we also reject all proposal if we already accepted one
                        if (worthIt.size() == 0 || accepted) {
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
                        ch.setOffer(worthIt.get(0));

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
            protected ACLMessage handleCfp(ACLMessage cfp) throws FailureException, NotUnderstoodException, RefuseException {

                try {
                    Action ac = (Action) getContentManager().extractContent(cfp);

                    SellMeBooks smb = (SellMeBooks) ac.getAction();
                    ArrayList<BookInfo> books = smb.getBooks();

                    ArrayList<BookInfo> sellBooks = new ArrayList<BookInfo>();

                    //find out, if we have books the agent wants
                    for(BookInfo advBook : books){
                        boolean found = false;
                        for(BookInfo myBook:myBooks) {
                            if (myBook.getBookName().equals(advBook.getBookName())) {
                                sellBooks.add(myBook);
                                found = true;
                                break;
                            }
                        }

                        if (!found)
                            throw new RefuseException("");
                    }

                    // Create offers
                    ArrayList<Offer> offers = createOffers(books);

                    // Offer message content
                    ChooseFrom cf = new ChooseFrom();
                    cf.setWillSell(sellBooks);
                    cf.setOffers(offers);

                    // Send the offers
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

                    soldBooks(cf.getWillSell(), c.getOffer().getBooks(), c.getOffer().getMoney());

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