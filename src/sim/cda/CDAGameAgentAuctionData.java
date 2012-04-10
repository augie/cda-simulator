package sim.cda;

import ab3d.auction.PQBid;
import ab3d.auction.Quote;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Augie
 */
public class CDAGameAgentAuctionData {

    public Integer id;
    public Quote quote;
    public int holdings = 0;
    public List<Integer> values;
    public List<PQBid> bids = new LinkedList<PQBid>();
    public boolean closed = false;

    public Integer currentValue() {
        if (values == null) {
            return null;
        }
        if (holdings >= values.size()) {
            return null;
        }
        return values.get(holdings);
    }

    public PQBid currentBid() {
        if (bids == null) {
            return null;
        }
        if (holdings >= bids.size()) {
            return null;
        }
        return bids.get(holdings);
    }

    public void setBid(PQBid bid) {
        if (holdings == bids.size() - 1) {
            bids.remove(holdings);
        }
        bids.add(bid);
    }
}
