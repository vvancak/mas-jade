package mas.cv3.onto;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

/**
 * Created by marti_000 on 8.4.14.
 */
public class BookInfo implements Concept{

    String name;
    Integer price;

    @Slot(mandatory = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Slot(mandatory = false)
    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }
}
