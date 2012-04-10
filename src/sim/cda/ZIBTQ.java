package sim.cda;

import ab3d.util.Log;

/**
 *
 * @author Augie <augie@umich.edu>
 */
public class ZIBTQ extends CDAGameAgent implements Runnable {

    public static final String LOG_ID = ZIBTQ.class.getSimpleName();

    public ZIBTQ(String host, int port, String agentID, String agentPW) {
        super(host, port, agentID, agentPW);
        initZIBTQ();
    }

    public ZIBTQ(String host, int port, String agentID, String agentPW, Log l) {
        super(host, port, agentID, agentPW, l);
        initZIBTQ();
    }

    public ZIBTQ() {
        super();
        initZIBTQ();
    }

    private void initZIBTQ() {
        logID = LOG_ID;
    }

    @Override
    public void submitBids() {
        // Generate and submit bid depending on role
        int from, to;
        if (isBuyer()) {
            // What is the last quoted bid?
            int beatTheBidQuote = CDAGameConstants.V_MIN;
            if (auctions[repetitionIndex].quote != null && auctions[repetitionIndex].quote.lastBidPrice != null && auctions[repetitionIndex].quote.lastBidPrice.intValue() > 0) {
                print("Last quoted bid price: " + auctions[repetitionIndex].quote.lastBidPrice.intValue());
                beatTheBidQuote = auctions[repetitionIndex].quote.lastBidPrice.intValue() + 1;
            }
            // Would generate negative utility, so at most bid value
            if (beatTheBidQuote > auctions[repetitionIndex].currentValue().intValue()) {
                beatTheBidQuote = auctions[repetitionIndex].currentValue().intValue();
            }
            print("Beats the bid quote: " + beatTheBidQuote);
            // Bid drawn uniformly at random from between BTQ and private value
            from = beatTheBidQuote;
            to = auctions[repetitionIndex].currentValue().intValue();
        } else {
            // What is the last quoted ask?
            int beatTheAskQuote = CDAGameConstants.V_MAX;
            if (auctions[repetitionIndex].quote != null && auctions[repetitionIndex].quote.lastAskPrice != null && auctions[repetitionIndex].quote.lastAskPrice.intValue() > 0) {
                print("Last quoted ask price: " + auctions[repetitionIndex].quote.lastAskPrice.intValue());
                beatTheAskQuote = auctions[repetitionIndex].quote.lastAskPrice.intValue() - 1;
            }
            // Would generate negative utility, so at least bid value
            if (beatTheAskQuote < auctions[repetitionIndex].currentValue().intValue()) {
                beatTheAskQuote = auctions[repetitionIndex].currentValue().intValue();
            }
            print("Beats the ask quote: " + beatTheAskQuote);
            // Bid drawn uniformly at random from between private value and BTQ
            from = auctions[repetitionIndex].currentValue().intValue();
            to = beatTheAskQuote;
        }
        print("Drawing U[" + from + ", " + to + "]");
        int bid;
        if (from >= to) {
            bid = from;
        } else {
            bid = Utils.RANDOM.nextInt(to - from + 1) + from;
        }
        submitBid(bid);
    }
}