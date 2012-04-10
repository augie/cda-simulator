package sim.cda;

import ab3d.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Augie <augie@umich.edu>
 */
public class RB extends CDAGameAgent implements Runnable {

    public static final String LOG_ID = RB.class.getSimpleName();
    public static final double THETA = 1;
    public static final double ETA = 4;
    public static final double BETA = 0.5;
    public static final double DELTA = 1;
    public static final int WINDOW_SIZE = 8;
    private double r;
    private Double tau = null;
    private int bidsIndex = 0, currentRepetition = -1;
    private Map<Integer, Set<CDAGameBid>> alreadyHandled = new HashMap<Integer, Set<CDAGameBid>>();

    public RB(String host, int port, String agentID, String agentPW) {
        super(host, port, agentID, agentPW);
        initRB();
    }

    public RB(String host, int port, String agentID, String agentPW, Log l) {
        super(host, port, agentID, agentPW, l);
        initRB();
    }

    public RB() {
        super();
        initRB();
    }

    private void initRB() {
        logID = LOG_ID;
    }

    @Override
    public void adjustState() {
        // New repetition?
        if (currentRepetition != repetitionIndex) {
            // Initialize r randomly from U[-0.2, 0.2]
            r = 0.4 * Utils.RANDOM.nextDouble() - 0.2;
            tau = null;
            bidsIndex = 0;
            alreadyHandled.put(repetitionIndex, new HashSet<CDAGameBid>());
            currentRepetition = repetitionIndex;
        }
        // Adjust r and tau if there has been at least one transaction
        if (auctions[repetitionIndex].currentValue() != null && Utils.TRANSACTED_BIDS.containsKey(repetitionIndex) && !Utils.TRANSACTED_BIDS.get(repetitionIndex).isEmpty()) {
            // Update the risk from all of the bids since last checking
            if (Utils.BIDS.containsKey(repetitionIndex)) {
                // Update the risk factor based on the events in the market
                for (; bidsIndex < Utils.BIDS.get(repetitionIndex).size(); bidsIndex++) {
                    CDAGameBid bid = Utils.BIDS.get(repetitionIndex).get(bidsIndex);

                    // Make sure this is not a transaction that was already handled
                    if (alreadyHandled.get(repetitionIndex).contains(bid)) {
                        continue;
                    }

                    // What is the average for the last WINDOW_SIZE (at most) previous transactions?
                    // Make a list of all transactions that occurred before this bid
                    List<CDAGameBid> prevTransactions = new ArrayList<CDAGameBid>();
                    for (int i = 0; i < Utils.TRANSACTED_BIDS.get(repetitionIndex).size(); i++) {
                        CDAGameBid toCopy = Utils.TRANSACTED_BIDS.get(repetitionIndex).get(i);
                        // This transaction occurred after the current bid
                        if (toCopy.timestamp.longValue() > bid.timestamp.longValue()) {
                            break;
                        }
                        // Add to the list
                        prevTransactions.add(toCopy);
                    }
                    // The last one should be a bid in order to make it a full transaction
                    if (!prevTransactions.isEmpty() && !prevTransactions.get(prevTransactions.size() - 1).agentIsBuyer) {
                        prevTransactions.remove(prevTransactions.size() - 1);
                    }
                    // Can't update tau without a transaction
                    if (prevTransactions.isEmpty()) {
                        continue;
                    }

                    // Calculate p*
                    double sum = 0, count = 0;
                    // How many transactions will be used in the average?
                    if (prevTransactions.size() >= WINDOW_SIZE * 2) {
                        count = WINDOW_SIZE;
                    } else {
                        count = prevTransactions.size() / 2;
                    }
                    // Add up the price for all of the transactions
                    for (int i = prevTransactions.size() - 1, num = 0; i >= 0 && num < count; i -= 2, num++) {
                        // Get the two bids
                        CDAGameBid bid1 = prevTransactions.get(i);
                        CDAGameBid bid2 = prevTransactions.get(i - 1);
                        // Get the transaction price (earliest of the two)
                        double q;
                        if (bid1.timestamp.longValue() <= bid2.timestamp.longValue()) {
                            q = bid1.price.doubleValue();
                        } else {
                            q = bid2.price.doubleValue();
                        }
                        // Increment sum
                        sum += q;
                    }
                    double pStar = sum / count;
                    print("p* = " + pStar);

                    // Update tau
                    Double newTau = null;
                    if (isBuyer()) {
                        if (r >= 0 && r <= 1) {
//                            newTau = pStar * (1 - r * Math.exp(THETA * (r - 1d)));
                            newTau = pStar - (pStar - CDAGameConstants.V_MIN) * r * Math.exp(THETA * (r - 1d));
                        } else if (r >= -1 && r < 0) {
//                            newTau = pStar + (auctions[repetitionIndex].currentValue().doubleValue() - pStar) * (1 - (r + 1) * Math.exp(r * ((pStar * Math.exp(-1d * THETA)) / (auctions[repetitionIndex].currentValue().doubleValue() - pStar) - 1d)));
                            newTau = pStar - (auctions[repetitionIndex].currentValue().doubleValue() - pStar) * r * Math.exp((r + 1d) * (Math.log((pStar - CDAGameConstants.V_MIN) / (auctions[repetitionIndex].currentValue().doubleValue() - pStar)) - THETA));
                        }
                    } else {
                        if (r >= 0 && r <= 1) {
                            newTau = pStar + (CDAGameConstants.V_MAX - pStar) * r * Math.exp((r - 1) * THETA);
                        } else if (r >= -1 && r < 0) {
                            newTau = pStar + (pStar - auctions[repetitionIndex].currentValue().doubleValue()) * r * Math.exp((r + 1d) * (Math.log((CDAGameConstants.V_MAX - pStar) / (pStar - auctions[repetitionIndex].currentValue().doubleValue())) - THETA));
                        }
                    }
                    // This happens
                    if (newTau == null || newTau.equals(Double.NaN)) {
                        continue;
                    }
                    print("new tau = " + newTau);
                    tau = newTau;

                    // In which direction should the risk factor be adjusted?
                    double direction = 0;
                    if (isBuyer()) {
                        // If this bid resulted in a transaction
                        if (Utils.isTransacted(repetitionIndex, bid)) {
                            // Get both bids to determine q
                            int index = Utils.TRANSACTED_BIDS.get(repetitionIndex).indexOf(bid);
                            CDAGameBid cBid;
                            if (bid.agentIsBuyer) {
                                // The corresponding ask is down one index
                                cBid = Utils.TRANSACTED_BIDS.get(repetitionIndex).get(index - 1);
                            } else {
                                // The corresponding bid is up one index
                                cBid = Utils.TRANSACTED_BIDS.get(repetitionIndex).get(index + 1);
                            }
                            alreadyHandled.get(repetitionIndex).add(cBid);
                            // Which was submitted first? 
                            double q;
                            if (bid.timestamp.longValue() < cBid.timestamp.longValue()) {
                                q = bid.price.doubleValue();
                            } else {
                                q = cBid.price.doubleValue();
                            }
                            // Increase risk factor if target >= transaction price
                            if (tau.doubleValue() >= q) {
                                direction = 1;
                            } // Otherwise decrease risk factor
                            else {
                                direction = -1;
                            }
                        } // This bid did not (immediately) result in a transaction
                        else if (bid.agentIsBuyer && tau.doubleValue() <= bid.price.intValue()) {
                            direction = -1;
                        }
                    } else {
                        // If this bid resulted in a transaction
                        if (Utils.isTransacted(repetitionIndex, bid)) {
                            // Get both bids to determine q
                            int index = Utils.TRANSACTED_BIDS.get(repetitionIndex).indexOf(bid);
                            CDAGameBid cBid;
                            if (bid.agentIsBuyer) {
                                // The corresponding ask is down one index
                                cBid = Utils.TRANSACTED_BIDS.get(repetitionIndex).get(index - 1);
                            } else {
                                // The corresponding bid is up one index
                                cBid = Utils.TRANSACTED_BIDS.get(repetitionIndex).get(index + 1);
                            }
                            alreadyHandled.get(repetitionIndex).add(cBid);
                            // Which was submitted first? 
                            double q;
                            if (bid.timestamp.longValue() < cBid.timestamp.longValue()) {
                                q = bid.price.doubleValue();
                            } else {
                                q = cBid.price.doubleValue();
                            }
                            // Increase risk factor if target <= transaction price
                            if (tau.doubleValue() <= q) {
                                direction = 1;
                            } // Otherwise decrease risk factor
                            else {
                                direction = -1;
                            }
                        } // This bid did not (immediately) result in a transaction
                        else if (!bid.agentIsBuyer && tau.doubleValue() >= bid.price.intValue()) {
                            direction = -1;
                        }
                    }
                    // No change to risk factor
                    if (direction == 0) {
                        continue;
                    }
                    // Adjust the risk factor
                    double delta = r * (1 + direction * 0.05) + direction * 0.025;
                    r = r + BETA * (delta - r);
                    print("r = " + r);
                }
            }
        }
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
        // Default outstanding ask and bid
        if (BID == null) {
            BID = CDAGameConstants.V_MIN;
        }
        if (ASK == null) {
            ASK = CDAGameConstants.V_MAX;
        }
        // Calculate a bid
        // From RB (for first if): Only submit a new bid if the BID/ASK does not produce negative utility (the previous bid will remain in the order book)
        if (isBuyer()) {
            // Can't beat the current outstanding bid, so do not bid
            if (BID.intValue() > auctions[repetitionIndex].currentValue().intValue()) {
                bid = CDAGameConstants.V_MIN;
            } // If the bid-ask spread is less than the delta spread, accept the ask
            else if (ASK.intValue() - BID.intValue() <= DELTA) {
                // A transaction appears imminent, so swoop in and take it
                bid = ASK.intValue();
            } // This is the beginning of the trading period 
            else if (tau == null) {
                bid = (int) Math.ceil(BID.doubleValue() + (Math.min(auctions[repetitionIndex].currentValue().doubleValue(), ASK.doubleValue()) - BID.doubleValue()) / ETA);
            } // There is a value for tau 
            else if (ASK.intValue() <= tau.doubleValue()) {
                // The ask is less than target, so snatch it up
                bid = ASK.intValue();
            } else {
                bid = (int) Math.ceil(BID.doubleValue() + (tau.doubleValue() - BID.doubleValue()) / ETA);
            }
        } else {
            // Can't beat the current outstanding bid, so do not bid
            if (ASK.intValue() < auctions[repetitionIndex].currentValue().intValue()) {
                bid = CDAGameConstants.V_MAX;
            } // if the bid-ask spread is less than the delta spread, accept the ask
            else if (ASK.intValue() - BID.intValue() <= DELTA) {
                // A transaction appears imminent, so swoop in and take it
                bid = BID.intValue();
            } // This is the beginning of the trading period
            else if (tau == null) {
                bid = (int) Math.floor(ASK.doubleValue() - (ASK.doubleValue() - Math.max(auctions[repetitionIndex].currentValue().doubleValue(), BID.doubleValue())) / ETA);
            } // There is a value for tau 
            else if (BID.intValue() >= tau.doubleValue()) {
                // The bid is higher than target, so snatch it up
                bid = BID.intValue();
            } else {
                bid = (int) Math.floor(ASK.doubleValue() - (ASK.doubleValue() - tau.doubleValue()) / ETA);
            }
        }
        // Dummy check... make sure the bid does not return negative utility
        if (isBuyer()) {
            // If the bid price is higher than private value, adjust
            if (bid > auctions[repetitionIndex].currentValue().intValue()) {
                bid = auctions[repetitionIndex].currentValue().intValue();
            }
        } else {
            // If the ask price is lower than private value, adjust
            if (bid < auctions[repetitionIndex].currentValue().intValue()) {
                bid = auctions[repetitionIndex].currentValue().intValue();
            }
        }
        // Submit the bid
        if (bid >= 0) {
            submitBid(bid);
        }
    }
}