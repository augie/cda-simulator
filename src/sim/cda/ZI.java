package sim.cda;

import ab3d.util.Log;

/**
 *
 * @author Augie <augie@umich.edu>
 */
public class ZI extends CDAGameAgent implements Runnable {

    public static final String LOG_ID = ZI.class.getSimpleName();

    public ZI(String host, int port, String agentID, String agentPW) {
        super(host, port, agentID, agentPW);
        initZI();
    }

    public ZI(String host, int port, String agentID, String agentPW, Log l) {
        super(host, port, agentID, agentPW, l);
        initZI();
    }

    public ZI() {
        super();
        initZI();
    }

    private void initZI() {
        logID = LOG_ID;
    }

    @Override
    public void submitBids() {
        // Generate and submit bid depending on role
        int from, to;
        if (isBuyer()) {
            // Bid drawn uniformly at random from between V_MIN and private value
            from = CDAGameConstants.V_MIN;
            to = auctions[repetitionIndex].currentValue().intValue();
        } else {
            // Bid drawn uniformly at random from between private value and V_MAX
            from = auctions[repetitionIndex].currentValue().intValue();
            to = CDAGameConstants.V_MAX;
        }
        print("Drawing U[" + from + ", " + (to + 1) + "]");
        int bid;
        if (from >= to) {
            bid = from;
        } else {
            bid = Utils.RANDOM.nextInt(to - from + 1) + from;
        }
        submitBid(bid);
    }
}