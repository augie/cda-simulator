package sim.cda;

import ab3d.auction.Transaction;
import java.io.OutputStream;
import java.io.Writer;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.net.SocketFactory;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Augie
 */
public class Utils {

    public static final Map<Integer, List<CDAGameBid>> BIDS = new HashMap<Integer, List<CDAGameBid>>();
    public static final Map<Integer, LinkedList<CDAGameBid>> BID_STACK = new HashMap<Integer, LinkedList<CDAGameBid>>(), ASK_STACK = new HashMap<Integer, LinkedList<CDAGameBid>>();
    public static final Map<Integer, List<CDAGameBid>> TRANSACTED_BIDS = new HashMap<Integer, List<CDAGameBid>>();
    public static final Map<Integer, List<Transaction>> TRANSACTIONS = new HashMap<Integer, List<Transaction>>();
    public static final Object BID_LOCK = new Object();
    public static final Random RANDOM = new Random();
    public static final String LINE_BREAK = System.getProperty("line.separator");

    public static void reset() {
        BIDS.clear();
        BID_STACK.clear();
        TRANSACTED_BIDS.clear();
        TRANSACTIONS.clear();
    }

    public static void addBid(int repetitionIndex, CDAGameBid bid) {
        // Add to bid list
        if (!BIDS.containsKey(repetitionIndex)) {
            BIDS.put(repetitionIndex, new ArrayList<CDAGameBid>());
        }
        BIDS.get(repetitionIndex).add(bid);
        // Update bid/ask stacks
        if (!BID_STACK.containsKey(repetitionIndex)) {
            BID_STACK.put(repetitionIndex, new LinkedList<CDAGameBid>());
        }
        if (!ASK_STACK.containsKey(repetitionIndex)) {
            ASK_STACK.put(repetitionIndex, new LinkedList<CDAGameBid>());
        }
        if (bid.agentIsBuyer) {
            if (BID_STACK.get(repetitionIndex).isEmpty() || BID_STACK.get(repetitionIndex).getLast().price.intValue() < bid.price.intValue()) {
                BID_STACK.get(repetitionIndex).add(bid);
            }
        } else {
            if (ASK_STACK.get(repetitionIndex).isEmpty() || ASK_STACK.get(repetitionIndex).getLast().price.intValue() > bid.price.intValue()) {
                ASK_STACK.get(repetitionIndex).add(bid);
            }
        }
        // Check for a transaction
        if (!TRANSACTED_BIDS.containsKey(repetitionIndex)) {
            TRANSACTED_BIDS.put(repetitionIndex, new LinkedList<CDAGameBid>());
        }
        if (!ASK_STACK.get(repetitionIndex).isEmpty() && !BID_STACK.get(repetitionIndex).isEmpty() && ASK_STACK.get(repetitionIndex).getLast().price.intValue() <= BID_STACK.get(repetitionIndex).getLast().price.intValue()) {
            TRANSACTED_BIDS.get(repetitionIndex).add(ASK_STACK.get(repetitionIndex).removeLast());
            TRANSACTED_BIDS.get(repetitionIndex).add(BID_STACK.get(repetitionIndex).removeLast());
        }
    }

    public static boolean isTransacted(int repetitionIndex, CDAGameBid bid) {
        if (!TRANSACTED_BIDS.containsKey(repetitionIndex)) {
            TRANSACTED_BIDS.put(repetitionIndex, new LinkedList<CDAGameBid>());
        }
        return TRANSACTED_BIDS.get(repetitionIndex).contains(bid);
    }

    public static void safeClose(OutputStream os) {
        if (os == null) {
            return;
        }
        try {
            os.flush();
        } catch (Exception e) {
        }
        try {
            os.close();
        } catch (Exception e) {
        }
    }

    public static void safeClose(Writer w) {
        if (w == null) {
            return;
        }
        try {
            w.flush();
        } catch (Exception e) {
        }
        try {
            w.close();
        } catch (Exception e) {
        }
    }

    public static String send(String msg, int port) throws Exception {
        Socket s = SocketFactory.getDefault().createSocket("localhost", port);
        StringBuilder response = new StringBuilder();
        try {
            IOUtils.write(msg + "\0", s.getOutputStream());
            char c;
            while ((c = (char) s.getInputStream().read()) != '\0') {
                response.append(c);
            }
        } finally {
            s.close();
        }
        return response.toString();
    }
}
