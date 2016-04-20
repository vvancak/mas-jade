package mas.cv4;

import mas.cv4.onto.BookInfo;
import mas.cv4.onto.AgentInfo;
import mas.cv4.onto.Goal;

import java.util.ArrayList;

/**
 * Created by Martin Pilat on 15.4.14.
 *
 * Basic utilities used in the book trading competition
 */
public class Utils {


    /** Compute the utility of the agent based on its money and books it has
     *
     *  The utility is the sum of values of the books owned by the agents and has them in its goals and the
     *  money the agent has. Books, the agents owns, but are not in his goals are not considered.
     */
    public static double computeUtility(AgentInfo ai) {

        double util = ai.getMoney();

        ArrayList<Goal> goals = ai.getGoals();
        ArrayList<BookInfo> books = ai.getBooks();

        boolean allGoals = true;
        for (Goal g : goals) {

            for (int i = 0; i < books.size(); i++) {
                if (books.get(i).getBookName().equals(g.getBook().getBookName())) {
                    util += g.getValue();
                    break;
                }
                allGoals = false;
            }
        }

        return util;

    }

    public static boolean hasAllBooks(AgentInfo ai) {

        ArrayList<Goal> goals = ai.getGoals();
        ArrayList<BookInfo> books = ai.getBooks();

        int nBooks = 0;
        for (Goal g : goals) {

            for (int i = 0; i < books.size(); i++) {
                if (books.get(i).getBookName().equals(g.getBook().getBookName())) {
                    nBooks++;
                    break;
                }
            }
        }

        return nBooks == goals.size();

    }
}
