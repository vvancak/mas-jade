package mas.cv4.onto;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

/**
 * Created by Martin Pilat on 12.2.14.
 *
 * Information on a book - name and ID
 */
public class BookInfo implements Concept {

    private String bookName;
    private int bookID;

    @Slot(mandatory = true)
    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public int getBookID() {
        return bookID;
    }

    public void setBookID(int bookID) {
        this.bookID = bookID;
    }

    public String toString() {
        return "[" + bookName + "," + bookID + "]";
    }

}
