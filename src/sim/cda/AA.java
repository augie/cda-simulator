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
public class AA extends CDAGameAgent implements Runnable {

    public static final String LOG_ID = AA.class.getSimpleName();
    public static final double ALPHA_MIN = 0;
    public static final double ALPHA_MAX = 0.2;
    public static final double GAMMA = 2;
    public static final double ETA = 4;
    public static final double THETA_MIN = -8;
    public static final double THETA_MAX = 4;
    public static final double THETA_INIT = -4;
    public static final double LAMBDA_R = 0.05;
    public static final double LAMBDA_A = 0.025;
    public static final double DELTA = 1;
    public static final double RHO = 0.9;
    public static final int WINDOW_SIZE = 8;
    private double r, beta1, beta2, theta;
    private Double tau = null;
    private int bidsIndex = 0, currentRepetition = -1;
    private Map<Integer, Set<CDAGameBid>> alreadyHandled = new HashMap<Integer, Set<CDAGameBid>>();

    public AA(String host, int port, String agentID, String agentPW) {
        super(host, port, agentID, agentPW);
        initAA();
    }

    public AA(String host, int port, String agentID, String agentPW, Log l) {
        super(host, port, agentID, agentPW, l);
        initAA();
    }

    public AA() {
        super();
        initAA();
    }

    private void initAA() {
        logID = LOG_ID;
        beta1 = 0.4 * Utils.RANDOM.nextDouble() + 0.2;
        beta2 = 0.4 * Utils.RANDOM.nextDouble() + 0.2;
    }

    @Override
    public void adjustState() {
        // New repetition?
        if (currentRepetition != repetitionIndex) {
            // Initialize r randomly from U[-0.2, 0.2]
            r = 0.4 * Utils.RANDOM.nextDouble() - 0.2;
            theta = THETA_INIT;
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
                    try {
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
                        
                        // Calculate the primary weight value which satisfies the given constraints
                        double[] weights = new double[(int) count];
                        double denom = 0;
                        for (int i = 0; i < count; i++) {
                            denom += Math.pow(RHO, i);
                        }
                        double weight = 1d / denom;
                        // Now set the p* weights
                        weights[0] = weight;
                        for (int i = 1; i < count; i++) {
                            weights[i] = Math.pow(RHO, i) * weight;
                        }
                        
                        // Add up the weighted price for all of the transactions
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
                            sum += weights[num] * q;
                        }
                        double pStar = sum;
                        print("p* = " + pStar);

                        // Just guessing
                        double thetaUnder = theta;

                        // Update tau
                        Double newTau = null;
                        if (isBuyer()) {
                            // Intra-marginal
                            if (auctions[repetitionIndex].currentValue().intValue() > pStar) {
                                if (r >= 0 && r <= 1) {
                                    newTau = pStar + (auctions[repetitionIndex].currentValue().doubleValue() - pStar) * ((Math.exp(r * theta) - 1d) / (Math.exp(theta) - 1d));
                                } else if (r >= -1 && r < 0) {
                                    newTau = pStar - (pStar - CDAGameConstants.V_MIN) * (Math.exp(-1d * r * thetaUnder) - 1d) / (Math.exp(thetaUnder - 1d));
                                }
                            } // Extra-marginal
                            else {
                                if (r >= 0 && r <= 1) {
                                    newTau = auctions[repetitionIndex].currentValue().doubleValue();
                                } else if (r >= -1 && r < 0) {
                                    newTau = auctions[repetitionIndex].currentValue().doubleValue() - (auctions[repetitionIndex].currentValue().doubleValue() - CDAGameConstants.V_MIN) * ((Math.exp(-1 * r * theta) - 1d) / (Math.exp(theta) - 1d));
                                }
                            }
                        } else {
                            // Intra-marginal
                            if (auctions[repetitionIndex].currentValue().intValue() < pStar) {
                                if (r >= 0 && r <= 1) {
                                    newTau = auctions[repetitionIndex].currentValue().doubleValue() + (pStar - auctions[repetitionIndex].currentValue().doubleValue()) * ((Math.exp(-1 * r * thetaUnder) - 1d) / (Math.exp(thetaUnder) - 1d));
                                } else if (r >= -1 && r < 0) {
                                    newTau = pStar + (CDAGameConstants.V_MAX - pStar) * ((Math.exp(-1 * r * thetaUnder) - 1d) / (Math.exp(thetaUnder) - 1d));
                                }
                            } // Extra-marginal
                            else {
                                if (r >= 0 && r <= 1) {
                                    newTau = auctions[repetitionIndex].currentValue().doubleValue();
                                } else if (r >= -1 && r < 0) {
                                    newTau = auctions[repetitionIndex].currentValue().doubleValue() + (CDAGameConstants.V_MAX - auctions[repetitionIndex].currentValue().doubleValue()) * ((Math.exp(-1 * r * theta) - 1d) / (Math.exp(theta) - 1d));
                                }
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
                        double delta = r * (1 + direction * LAMBDA_R) + direction * LAMBDA_A;
                        r = r + beta1 * (delta - r);
                        print("r = " + r);

                        // Calculate alpha
                        double alpha = 0;
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
                            alpha += Math.pow(q - pStar, 2);
                        }
                        alpha = Math.sqrt(alpha / count) / pStar;

                        // Calculate alpha bar
                        double alphaBar = (alpha - ALPHA_MIN) / (ALPHA_MAX - ALPHA_MIN);

                        // Calculate theta star
                        double thetaStar = (THETA_MAX - THETA_MIN) * (1d - alphaBar * Math.exp(GAMMA * (alphaBar - 1d))) + THETA_MIN;

                        // Adjust the theta parameter
                        theta = theta + beta2 * (thetaStar - theta);
                    } catch (Exception e) {
                        if (e.getMessage() != null) {
                            Main.printLog("AA error: " + e.getMessage());
                        } else {
                            Main.printLog("AA error: null");
                        }
                    }
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
            if (BID.intValue() >= auctions[repetitionIndex].currentValue().intValue()) {
                bid = CDAGameConstants.V_MIN;
            } // This is the beginning of the trading period 
            else if (tau == null) {
                double askPlus = (1d + LAMBDA_R) * ASK.doubleValue() + LAMBDA_A;
                bid = (int) Math.ceil(BID.doubleValue() + (Math.min(auctions[repetitionIndex].currentValue().doubleValue(), askPlus) - BID.doubleValue()) / ETA);
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
            } // This is the beginning of the trading period
            else if (tau == null) {
                double bidMinus = (1d - LAMBDA_R) * BID.doubleValue() - LAMBDA_A;
                bid = (int) Math.floor(ASK.doubleValue() - (ASK.doubleValue() - Math.max(auctions[repetitionIndex].currentValue().doubleValue(), bidMinus)) / ETA);
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
