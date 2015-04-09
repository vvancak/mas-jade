package mas.cv3.onto;

import jade.content.onto.BeanOntology;
import jade.content.onto.BeanOntologyException;

/**
 * Created by marti_000 on 8.4.14.
 */
public class BookOntology extends BeanOntology {

    static BookOntology theInstance = null;

    private BookOntology() {
        super("book-ontology");

        try {
            add("mas.cv3.onto");
        }
        catch (BeanOntologyException be) {
            be.printStackTrace();
        }
    }

    public static BookOntology getInstance() {
        if (theInstance == null)
            theInstance = new BookOntology();

        return theInstance;
    }

}
