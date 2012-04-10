package sim.cda;

import ab3d.auction.Transaction;
import ab3d.util.Log;

/**
 *
 * @author Augie <augie@umich.edu>
 */
public class KAPLAN extends CDAGameAgent implements Runnable {

    public static final String LOG_ID = KAPLAN.class.getSimpleName();
    public double Fs, Fp, Ft;

    public KAPLAN(String host, int port, String agentID, String agentPW) {
        super(host, port, agentID, agentPW);
        initKAPLAN();
    }

    public KAPLAN(String host, int port, String agentID, String agentPW, Log l) {
        super(host, port, agentID, agentPW, l);
        initKAPLAN();
    }

    public KAPLAN() {
        super();
        initKAPLAN();
    }

    private void initKAPLAN() {
        logID = LOG_ID;
        Fs = 0.0625;
        Fs += (Utils.RANDOM.nextDouble() - 0.5) * Fs;
        Fp = 0.1;
        Fp += (Utils.RANDOM.nextDouble() - 0.5) * Fp;
        Ft = 0.1;
        Ft += (Utils.RANDOM.nextDouble() - 0.5) * Ft;
    }

    @Override
    public void submitBids() {
        int bid = -1;
        // Current bid and ask spread
        Integer BID = null, ASK = null;
        if (auctions[repetitionIndex].quote != null && auctions[repetitionIndex].quote.lastBidPrice != null && auctions[repetitionIndex].quote.lastBidPrice.intValue() > 0) {
            BID = auctions[repetitionIndex].quote.lastBidPrice.intValue();
            print("BID: " + BID.intValue());
        }
        if (auctions[repetitionIndex].quote != null && auctions[repetitionIndex].quote.lastAskPrice != null && auctions[repetitionIndex].quote.lastAskPrice.intValue() > 0) {
            ASK = auctions[repetitionIndex].quote.lastAskPrice.intValue();
            print("ASK: " + ASK.intValue());
        }
        // Get the min and max priced transactions from last round
        Transaction minPriceTransaction = null, maxPriceTransaction = null;
        print("TRANSACTIONS SIZE: " + Utils.TRANSACTIONS.size());
        if (Utils.TRANSACTIONS.containsKey(repetitionIndex)) {
            print("TRANSACTIONS FOR REP " + repetitionIndex + ": " + Utils.TRANSACTIONS.get(repetitionIndex).size());
        }
        if (repetitionIndex > 0 && Utils.TRANSACTIONS.containsKey(repetitionIndex - 1)) {
            for (Transaction t : Utils.TRANSACTIONS.get(repetitionIndex - 1)) {
                if (minPriceTransaction == null || t.price.intValue() < minPriceTransaction.price.intValue()) {
                    minPriceTransaction = t;
                }
                if (maxPriceTransaction == null || t.price.intValue() > maxPriceTransaction.price.intValue()) {
                    maxPriceTransaction = t;
                }
            }
        }
        if (minPriceTransaction != null) {
            print("Last round's min transaction price: " + minPriceTransaction.price.intValue());
        }
        if (maxPriceTransaction != null) {
            print("Last round's max transaction price: " + maxPriceTransaction.price.intValue());
        }
        // How much time is remaining in this bid?
        long timeRemaining = startTime.longValue() + (repetitionIndex + 1) * CDAGameConstants.AUCTION_LEN - CDAGameConstants.AUCTION_BUFFER_LEN - now().longValue();
        // Calculate bid
        if (isBuyer()) {
            int toBid = CDAGameConstants.V_MIN;
            // The amount to bid is the quoted ask price
            if (ASK != null) {
                toBid = ASK.intValue();
            }
            // If the quoted ask price is higher than private value, then no bid can be made
            if (toBid > auctions[repetitionIndex].currentValue().intValue()) {
                print("Can't bid, value not high enough");
                return;
            }
            // The amount to bid is less than the minimum amount the good was traded for in the last auction 
            if (minPriceTransaction != null && toBid < minPriceTransaction.price.intValue()) {
                bid = toBid;
                print("Buyer, first");
            }
            // The amount to bid is less than the maximum transaction price in the last auction
            //  and the ratio of the BID-ASK spread to the ASK price is less than Fs
            //  and the ratio of expected profit to maximum possible profit is more than Fp
            if (BID != null && ASK != null && maxPriceTransaction != null) {
                double expectedProfit = auctions[repetitionIndex].currentValue().intValue() - toBid;
                double maximumProfit = auctions[repetitionIndex].currentValue().intValue() - CDAGameConstants.V_MIN;
                if (toBid < maxPriceTransaction.price.intValue()
                        && ((double) ASK - BID) / (double) ASK < Fs
                        && expectedProfit / maximumProfit > Fp) {
                    bid = toBid;
                    print("Buyer, second");
                }
            }
            // The fraction time remaining in the auction is less than Ft
            if ((double) timeRemaining / (double) (CDAGameConstants.AUCTION_LEN - CDAGameConstants.AUCTION_BUFFER_LEN) < Ft) {
                bid = toBid;
                print("Little time remaining");
            }
        } else {
            // Match the highest bid price
            int toAsk = CDAGameConstants.V_MAX;
            // The amount to ask is the quoted bid price
            if (BID != null) {
                toAsk = BID.intValue();
            }
            // If the quoted bid price is lower than private value, then no ask can be made
            if (toAsk < auctions[repetitionIndex].currentValue().intValue()) {
                print("Can't ask, value not low enough");
                return;
            }
            // The amount to ask is more than the maximum amount the good was traded for in the last auction 
            if (maxPriceTransaction != null && toAsk > maxPriceTransaction.price.intValue()) {
                bid = toAsk;
                print("Seller, first");
            }
            // The amount to ask is more than the minimum transaction price in the last auction
            //  and the ratio of the BID-ASK spread to the BID price is less than Fs
            //  and the ratio of expected profit to maximum possible profit is more than Fp
            if (BID != null && ASK != null && minPriceTransaction != null) {
                double expectedProfit = toAsk - auctions[repetitionIndex].currentValue().intValue();
                double maximumProfit = CDAGameConstants.V_MAX - auctions[repetitionIndex].currentValue().intValue();
                if (toAsk > minPriceTransaction.price.intValue()
                        && ((double) ASK - BID) / (double) BID < Fs
                        && expectedProfit / maximumProfit > Fp) {
                    bid = toAsk;
                    print("Seller, second");
                }
            }
            // The fraction time remaining in the auction is less than Ft
            if ((double) timeRemaining / (double) (CDAGameConstants.AUCTION_LEN - CDAGameConstants.AUCTION_BUFFER_LEN) < Ft) {
                bid = toAsk;
                print("Little time remaining");
            }
        }
        // Maybe do not bid
        if (bid >= 0) {
            submitBid(bid);
        }
    }
}
