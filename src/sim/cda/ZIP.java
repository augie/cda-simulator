package sim.cda;

import ab3d.util.Log;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Augie <augie@umich.edu>
 */
public class ZIP extends CDAGameAgent implements Runnable {

    public static final String LOG_ID = ZIP.class.getSimpleName();
    private Map<Integer, List<Double>> profitMargins = new HashMap<Integer, List<Double>>();
    private int nextBidIndex = 0, currentRepetition = 0;
    private double learningRate, momentum, ques = 0;

    public ZIP(String host, int port, String agentID, String agentPW) {
        super(host, port, agentID, agentPW);
        initZIP();
    }

    public ZIP(String host, int port, String agentID, String agentPW, Log l) {
        super(host, port, agentID, agentPW, l);
        initZIP();
    }

    public ZIP() {
        super();
        initZIP();
    }

    private void initZIP() {
        logID = LOG_ID;
        learningRate = 0.4 * Utils.RANDOM.nextDouble() + 0.1;
        momentum = 0.1 * Utils.RANDOM.nextDouble();
    }

    @Override
    public void adjustState() {
        print("");

        // Reset the tracking variables
        if (currentRepetition != repetitionIndex) {
            print("Resetting for next repetition.");
            nextBidIndex = 0;
            currentRepetition = repetitionIndex;
        }

        // There haven't been any bids in this auction
        if (!Utils.BIDS.containsKey(repetitionIndex) || Utils.BIDS.get(repetitionIndex).isEmpty()) {
            print("Not adjusting margin because there have been no bids yet.");
            return;
        }

        // Is that agent an active trader?
        //  When inactive, adjust the margin for the final unit
        boolean active = auctions[repetitionIndex].holdings < CDAGameConstants.MAX_TRADES;

        // Do not deal with inactive traders (this is unlikely to occur anyway)
        if (!active) {
            print("Inactive trader. Not adjusting margin.");
            return;
        }

        // Let the active trader make a first bid on this item before modifying a margin
        if (auctions[repetitionIndex].currentBid() == null || !profitMargins.containsKey(repetitionIndex) || profitMargins.get(repetitionIndex).size() <= auctions[repetitionIndex].holdings) {
            print("Not adjusting margin because there is no current bid.");
            return;
        }

        // Choose an R and A for this bidding iteration
        double R = 0.05 * Utils.RANDOM.nextDouble();
        double A = 5 * Utils.RANDOM.nextDouble();

        // Current bid price
        Integer currentBid = null;
        if (auctions[repetitionIndex].currentBid() != null) {
            currentBid = auctions[repetitionIndex].currentBid().bidArray[0].getprice().intValue();
            print("current bid: " + currentBid);
        }

        // Examine all the shouts that occurred since last check
        //  Hacked around AB3D, which doesn't make this information available
        for (; nextBidIndex < Utils.BIDS.get(repetitionIndex).size(); nextBidIndex++) {
            try {
                CDAGameBid gameBid = Utils.BIDS.get(repetitionIndex).get(nextBidIndex);

                // Did this bid result in a transaction?
                boolean accepted = Utils.isTransacted(repetitionIndex, gameBid);

                // Working margin and bid
                double margin = profitMargins.get(repetitionIndex).get(auctions[repetitionIndex].holdings);
                double pt = auctions[repetitionIndex].currentValue().doubleValue() * (1d + margin);
                if (isBuyer()) {
                    pt = Math.floor(pt);
                    if (pt > auctions[repetitionIndex].currentValue().intValue()) {
                        pt = auctions[repetitionIndex].currentValue().intValue();
                    }
                } else {
                    pt = Math.ceil(pt);
                    if (pt < auctions[repetitionIndex].currentValue().intValue()) {
                        pt = auctions[repetitionIndex].currentValue().intValue();
                    }
                }

                print("q: " + gameBid.price.intValue());
                print("buyer? " + gameBid.agentIsBuyer);
                print("accepted? " + accepted);
                print("p: " + pt);
                print("margin: " + margin);

                // Should the agent raise or lower the margin?
                //  0 means do not modify, 1 means raise, -1 means lower
                short modifyMargin = 0;
                if (isBuyer()) {
                    // if the last shout was accepted at price q
                    if (accepted) {
                        // if pt >= q
                        if (pt >= gameBid.price.intValue()) {
                            // raise the profit margin
                            modifyMargin = -1;
                        }
                        // if the last shout was an offer
                        if (!gameBid.agentIsBuyer) {
                            // if pt <= q
                            if (pt <= gameBid.price.intValue()) {
                                // lower the profit margin
                                modifyMargin = 1;
                            }
                        }
                    } else {
                        // if the last shout was a bid
                        if (gameBid.agentIsBuyer) {
                            // If pt <= q
                            if (pt <= gameBid.price.intValue()) {
                                // lower the profit margin
                                modifyMargin = 1;
                            }
                        }
                    }
                } else {
                    // if the last shout was accepted at price q
                    if (accepted) {
                        // if pt <= q
                        if (pt <= gameBid.price.intValue()) {
                            // raise the profit margin
                            modifyMargin = 1;
                        }
                        // if the last shout was a bid
                        if (gameBid.agentIsBuyer) {
                            // if pt >= q
                            if (pt >= gameBid.price.intValue()) {
                                // lower the profit margin
                                modifyMargin = -1;
                            }
                        }
                    } else {
                        // if the last shout was an ask
                        if (!gameBid.agentIsBuyer) {
                            // If pt >= q
                            if (pt >= gameBid.price.intValue()) {
                                // lower the profit margin
                                modifyMargin = -1;
                            }
                        }
                    }
                }

                // If no modification to the margin is needed, we're done here
                if (modifyMargin == 0) {
                    print("Not modifying margin");
                    continue;
                }

                // Adjust R and A (positive for price increase)
                double bidR = R, bidA = A;
                if (isBuyer()) {
                    if (modifyMargin > 0) {
                        bidR *= -1;
                        bidA *= -1;
                    }
                } else {
                    if (modifyMargin < 0) {
                        bidR *= -1;
                        bidA *= -1;
                    }
                }
                bidR += 1d;

                // Calculate target price
                double target = bidR * gameBid.price.intValue() + bidA;
                
                print("Target: " + target);

                // Adjust the margin
                double dt = learningRate * (target - pt);
                margin = ((pt + ques) / auctions[repetitionIndex].currentValue().doubleValue()) - 1d;
                ques = momentum * ques + (1 - momentum) * dt;
                
                print("New margin: " + margin);

                // Dummy checks
                if (isBuyer()) {
                    // Buyer margin should be negative.
                    if (margin > 0d) {
                        margin = 0d;
                    }
                } else {
                    // Seller margin should be positive
                    if (margin < 0d) {
                        margin = 0d;
                    }
                }

                // Save the computed margin
                if (profitMargins.get(repetitionIndex).size() > 0) {
                    // Remove the last one in the list
                    profitMargins.get(repetitionIndex).remove(profitMargins.get(repetitionIndex).size() - 1);
                }
                // Add the margin to the end of the list
                profitMargins.get(repetitionIndex).add(margin);
                print("New margin: " + margin);
            } catch (Exception e) {
                try {
                    Main.printLog("Error: " + e.getMessage());
                } catch (Exception ee) {
                }
            }
        }
    }

    @Override
    public void submitBids() {
        int bid = -1;
        // First bid in this repetition
        if (!profitMargins.containsKey(repetitionIndex)) {
            profitMargins.put(repetitionIndex, new LinkedList<Double>());
        }
        // New bid (beginning of auction or just sold previous item)
        if (profitMargins.get(repetitionIndex).size() <= auctions[repetitionIndex].holdings) {
            // Initialize new margins randomly to request at least the margin requested in the previous period
            int atLeast = auctions[repetitionIndex].currentValue().intValue();
            // Bootstrap off of the bid for this item from the previous auction
            if (repetitionIndex > 0) {
                if (auctions[repetitionIndex - 1].bids.size() > auctions[repetitionIndex].holdings) {
                    atLeast = auctions[repetitionIndex - 1].bids.get(auctions[repetitionIndex].holdings).bidArray[0].getprice().intValue();
                }
            }
            print("");
            print("First bid for item " + auctions[repetitionIndex].holdings + ", value " + auctions[repetitionIndex].currentValue().intValue());
            print("At least " + atLeast);
            // Generate and submit random bid depending on role
            double margin;
            if (isBuyer()) {
                // Bid drawn uniformly at random from between V_MIN and atLeast
                int n = atLeast - CDAGameConstants.V_MIN + 1;
                if (n <= 0) {
                    bid = CDAGameConstants.V_MIN;
                } else {
                    bid = Utils.RANDOM.nextInt(n) + CDAGameConstants.V_MIN;
                }
                margin = -1d * (double) (auctions[repetitionIndex].currentValue().intValue() - bid) / auctions[repetitionIndex].currentValue().doubleValue();
                // Dummy check. Buyer margin should be negative.
                if (margin > 0d) {
                    margin = 0d;
                }
            } else {
                // Bid drawn uniformly at random from between atLeast and V_MAX
                int n = CDAGameConstants.V_MAX - atLeast + 1;
                if (n <= 0) {
                    bid = atLeast;
                } else {
                    bid = Utils.RANDOM.nextInt(n) + atLeast;
                }
                margin = (double) (bid - auctions[repetitionIndex].currentValue().intValue()) / auctions[repetitionIndex].currentValue().doubleValue();
                // Dummy check. Seller margin should be positive
                if (margin < 0d) {
                    margin = 0d;
                }
            }
            // Save margin
            profitMargins.get(repetitionIndex).add(margin);
            print("New bid " + bid + " (" + margin + ")");
        } else {
            // Get the margin for the current unit
            double margin = profitMargins.get(repetitionIndex).get(auctions[repetitionIndex].holdings);

            // Calculate bid, rounding appropriately
            double bidDouble = auctions[repetitionIndex].currentValue().doubleValue() * (1d + margin);
            if (isBuyer()) {
                bid = (int) Math.ceil(bidDouble);
            } else {
                bid = (int) Math.floor(bidDouble);
            }

            // Make sure the bid does not return negative utility
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
        }
        if (bid >= 0) {
            submitBid(bid);
        }
    }
}
