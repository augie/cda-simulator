package sim.cda;

import ab3d.auction.Transaction;
import ab3d.util.Log;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 *
 * @author Augie <augie@umich.edu>
 */
public class GDX extends CDAGameAgent implements Runnable {

    public static final String LOG_ID = GDX.class.getSimpleName();
    public static final double GAMMA = 0.9;
    public static final int M = 7;
    // The history information is collectively used by all GDX agents
    private static final Map<Integer, LinkedList<CDAGameBid>> history = new HashMap<Integer, LinkedList<CDAGameBid>>();
    // Bid stack is ordered from lowest to highest, and ask stack is ordered from highest to lowest
    private static final LinkedList<CDAGameBid> bidStack = new LinkedList<CDAGameBid>(), askStack = new LinkedList<CDAGameBid>();
    private static final LinkedList<CDAGameBid> acceptedBids = new LinkedList<CDAGameBid>(), acceptedAsks = new LinkedList<CDAGameBid>();
    private static final Map<Integer, Double> buyerPr = new HashMap<Integer, Double>(), sellerPr = new HashMap<Integer, Double>();
    private static int bidsIndex = 0, currentRepetition = -1;
    private final Map<Integer, Map<Integer, Double>> V = new HashMap<Integer, Map<Integer, Double>>();
    private final Map<Integer, Map<Integer, Map<Integer, Double>>> Vp = new HashMap<Integer, Map<Integer, Map<Integer, Double>>>();
    private final Map<Integer, Map<Integer, Integer>> argMaxP = new HashMap<Integer, Map<Integer, Integer>>();

    public GDX(String host, int port, String agentID, String agentPW) {
        super(host, port, agentID, agentPW);
        logID = LOG_ID;
    }

    public GDX(String host, int port, String agentID, String agentPW, Log l) {
        super(host, port, agentID, agentPW, l);
        logID = LOG_ID;
    }

    public GDX() {
        super();
        logID = LOG_ID;
    }
    
    public static void reset() {
        history.clear();
        bidStack.clear();
        askStack.clear();
        acceptedBids.clear();
        acceptedAsks.clear();
        buyerPr.clear();
        sellerPr.clear();
        bidsIndex = 0;
        currentRepetition = -1;
    }

    @Override
    public void adjustState() {
        // Is this a new repetition
        if (currentRepetition != repetitionIndex) {
            bidsIndex = 0;
            history.put(repetitionIndex, new LinkedList<CDAGameBid>());
            bidStack.clear();
            askStack.clear();
            acceptedBids.clear();
            acceptedAsks.clear();
            currentRepetition = repetitionIndex;
        }
        // Update the history from all of the bids since last checking
        if (Utils.BIDS.containsKey(repetitionIndex)) {
            for (; bidsIndex < Utils.BIDS.get(repetitionIndex).size(); bidsIndex++) {
                CDAGameBid bid = Utils.BIDS.get(repetitionIndex).get(bidsIndex);
                // Only remember valid bids (those that beat the outstanding bid/ask)
                if ((bid.agentIsBuyer && (bidStack.isEmpty() || bid.price > bidStack.getLast().price.intValue()))
                        || (!bid.agentIsBuyer && (askStack.isEmpty() || bid.price < askStack.getLast().price.intValue()))) {
                    // Remember the bid
                    history.get(repetitionIndex).add(bid);
                    // This matching is as the auction runs
                    if (bid.agentIsBuyer && (bidStack.isEmpty() || bid.price.intValue() > bidStack.getLast().price.intValue())) {
                        bidStack.add(bid);
                    }
                    if (!bid.agentIsBuyer && (askStack.isEmpty() || bid.price.intValue() < askStack.getLast().price.intValue())) {
                        askStack.add(bid);
                    }
                    // Check for matching
                    if (!bidStack.isEmpty() && !askStack.isEmpty() && bidStack.getLast().price.intValue() >= askStack.getLast().price.intValue()) {
                        // Pop the outstanding bid and ask off the top of the stack
                        acceptedBids.add(bidStack.removeLast());
                        acceptedAsks.add(askStack.removeLast());
                    }
                }
            }
        }
        // Remove transactions overflowing memory
        int transactionCount = acceptedBids.size();
        while (transactionCount > M + 1) {
            acceptedBids.removeFirst();
            acceptedAsks.removeFirst();
            transactionCount--;
        }
        // Remove bids from memory up to the first bid after (M + 1)st transaction
        if (transactionCount == M + 1) {
            // Remove the first transaction (want all bids that come after it)
            CDAGameBid transactionBid = acceptedBids.removeFirst();
            CDAGameBid transactionAsk = acceptedAsks.removeFirst();
            // What is the timestamp of the transaction? Latest of the two
            long transactionTimestamp = transactionBid.timestamp.longValue();
            if (transactionAsk.timestamp.longValue() > transactionTimestamp) {
                transactionTimestamp = transactionAsk.timestamp.longValue();
            }
            // Count the number of bids that need to be remove from the front of the history
            int removeBids = 0;
            for (CDAGameBid bid : history.get(repetitionIndex)) {
                if (bid.timestamp.longValue() > transactionTimestamp) {
                    break;
                }
                removeBids++;
            }
            // Remove bids from the front of the list
            print("Removing # bids: " + removeBids);
            while (removeBids > 0) {
                history.get(repetitionIndex).removeFirst();
                removeBids--;
            }
            // Count the number of bids that need to be removed from the front of the bid stack
            removeBids = 0;
            for (CDAGameBid bid : bidStack) {
                if (bid.timestamp.longValue() > transactionTimestamp) {
                    break;
                }
                removeBids++;
            }
            while (removeBids > 0) {
                bidStack.removeFirst();
                removeBids--;
            }
            // Count the number of asks that need to be removed from the front of the ask stack
            removeBids = 0;
            for (CDAGameBid bid : askStack) {
                if (bid.timestamp.longValue() > transactionTimestamp) {
                    break;
                }
                removeBids++;
            }
            while (removeBids > 0) {
                askStack.removeFirst();
                removeBids--;
            }
            // Count the number of accepted asks that need to be removed from the front of the accepted ask stack
            removeBids = 0;
            for (CDAGameBid bid : acceptedAsks) {
                if (bid.timestamp.longValue() > transactionTimestamp) {
                    break;
                }
                removeBids++;
            }
            while (removeBids > 0) {
                acceptedAsks.removeFirst();
                removeBids--;
            }
        }
        print("# bids in memory: " + history.get(repetitionIndex).size());
        if (!history.get(repetitionIndex).isEmpty()) {
            print("Last bid price: " + history.get(repetitionIndex).getLast().price.intValue() + ", buyer? " + history.get(repetitionIndex).getLast().agentIsBuyer);
        }
        print("# transactions in memory: " + acceptedBids.size());

        // Compute probabilities
        for (int p = CDAGameConstants.V_MIN; p <= CDAGameConstants.V_MAX; p++) {
            buyerPr.put(p, Pr(true, p));
            sellerPr.put(p, Pr(false, p));
        }

        // Seller pr interpolation
        {
            // What are the interpolation points?
            Integer lastNonNaN = null, nextNonNaN = null;
            for (int p = CDAGameConstants.V_MIN; p <= CDAGameConstants.V_MAX; p++) {
                if (sellerPr.get(p).equals(Double.NaN) && lastNonNaN == null) {
                    // Nothing to interpolate here
                    if (p == CDAGameConstants.V_MIN) {
                        print("WHAT DO NOW? Bid stack size: " + bidStack.size());
                        break;
                    } else {
                        lastNonNaN = p - 1;
                    }
                }
                if (!sellerPr.get(p).equals(Double.NaN) && lastNonNaN != null) {
                    nextNonNaN = p;
                    break;
                }
            }
            if (lastNonNaN != null && nextNonNaN != null) {
                // Interpolate
                double ak = lastNonNaN.doubleValue(), akpo = nextNonNaN.doubleValue();
                print("Interpolating between (" + ak + ", " + sellerPr.get(lastNonNaN.intValue()) + ") and (" + akpo + ", " + sellerPr.get(nextNonNaN.intValue()) + ")");
                double[][] left = new double[][]{{ak * ak * ak, ak * ak, ak, 1}, {akpo * akpo * akpo, akpo * akpo, akpo, 1}, {3 * ak * ak, 2 * ak, 1, 0}, {3 * akpo * akpo, 2 * akpo, 1, 0}};
                RealMatrix coefficients = new Array2DRowRealMatrix(left, false);
                DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();
                double[] right = new double[]{sellerPr.get(lastNonNaN.intValue()), sellerPr.get(nextNonNaN.intValue()), 0, 0};
                RealVector constants = new ArrayRealVector(right, false);
                RealVector interpolation = solver.solve(constants);
                // Save results
                for (int innerP = lastNonNaN.intValue() + 1; innerP <= nextNonNaN.intValue() - 1; innerP++) {
                    double pr = interpolation.getEntry(0) * innerP * innerP * innerP + interpolation.getEntry(1) * innerP * innerP + interpolation.getEntry(2) * innerP + interpolation.getEntry(3);
                    sellerPr.put(innerP, pr);
                    print(" (" + innerP + ", " + pr + ")");
                }
                //  For MGD, set boundary conditions to 0 (or 1) after interpolation rather than before
                sellerPr.put(lastNonNaN.intValue(), 1d);
                sellerPr.put(nextNonNaN.intValue(), 0d);
            } else if (lastNonNaN != null && nextNonNaN == null) {
                for (int p = lastNonNaN.intValue() + 1; p <= CDAGameConstants.V_MAX; p++) {
                    sellerPr.put(p, 0d);
                }
            }
        }

        // Buyer pr interpolation
        {
            // What are the interpolation points?
            Integer lastNonNaN = null, nextNonNaN = null;
            for (int p = CDAGameConstants.V_MAX; p >= CDAGameConstants.V_MIN; p--) {
                if (buyerPr.get(p).equals(Double.NaN) && lastNonNaN == null) {
                    // Nothing to interpolate here
                    if (p == CDAGameConstants.V_MAX) {
                        print("WHAT DO NOW? Ask stack size: " + askStack.size());
                        break;
                    } else {
                        lastNonNaN = p + 1;
                    }
                }
                if (!buyerPr.get(p).equals(Double.NaN) && lastNonNaN != null) {
                    nextNonNaN = p;
                    break;
                }
            }
            if (lastNonNaN != null && nextNonNaN != null) {
                // Interpolate
                double ak = lastNonNaN.doubleValue(), akpo = nextNonNaN.doubleValue();
                print("Interpolating between (" + ak + ", " + buyerPr.get(lastNonNaN.intValue()) + ") and (" + akpo + ", " + buyerPr.get(nextNonNaN.intValue()) + ")");
                double[][] left = new double[][]{{ak * ak * ak, ak * ak, ak, 1}, {akpo * akpo * akpo, akpo * akpo, akpo, 1}, {3 * ak * ak, 2 * ak, 1, 0}, {3 * akpo * akpo, 2 * akpo, 1, 0}};
                RealMatrix coefficients = new Array2DRowRealMatrix(left, false);
                DecompositionSolver solver = new LUDecomposition(coefficients).getSolver();
                double[] right = new double[]{buyerPr.get(lastNonNaN), buyerPr.get(nextNonNaN), 0, 0};
                RealVector constants = new ArrayRealVector(right, false);
                RealVector interpolation = solver.solve(constants);
                // Save results
                for (int innerP = lastNonNaN.intValue() - 1; innerP >= nextNonNaN.intValue() + 1; innerP--) {
                    double pr = interpolation.getEntry(0) * innerP * innerP * innerP + interpolation.getEntry(1) * innerP * innerP + interpolation.getEntry(2) * innerP + interpolation.getEntry(3);
                    buyerPr.put(innerP, pr);
                    print(" (" + innerP + ", " + pr + ")");
                }
                //  For MGD, set boundary conditions to 0 (or 1) after interpolation rather than before
                buyerPr.put(lastNonNaN.intValue(), 1d);
                buyerPr.put(nextNonNaN.intValue(), 0d);
            } else if (lastNonNaN != null && nextNonNaN == null) {
                for (int p = lastNonNaN.intValue() - 1; p >= CDAGameConstants.V_MIN; p--) {
                    buyerPr.put(p, 0d);
                }
            }
        }

        // If this is not the first repetition (MGD add-on)
        if (repetitionIndex > 0) {
            // Get the min and max priced transactions from last round
            Transaction minPriceTransaction = null, maxPriceTransaction = null;
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
            // Set pr to 0 (seller) / 1 (buyer) for all prices above max transaction price
            if (maxPriceTransaction != null) {
                for (int p = CDAGameConstants.V_MAX; p > maxPriceTransaction.price.intValue(); p--) {
                    buyerPr.put(p, 1d);
                    sellerPr.put(p, 0d);
                }
            }
            // Set pr to 1 (seller) / 0 (buyer) for all prices below min transaction price
            if (minPriceTransaction != null) {
                for (int p = CDAGameConstants.V_MIN; p < minPriceTransaction.price.intValue(); p++) {
                    buyerPr.put(p, 0d);
                    sellerPr.put(p, 1d);
                }
            }
        }

        // Set the remaining NaN to 0
        for (int p = CDAGameConstants.V_MIN; p <= CDAGameConstants.V_MAX; p++) {
            if (sellerPr.get(p).equals(Double.NaN)) {
                sellerPr.put(p, 0d);
                print("NaN replaced with 0");
            }
            if (buyerPr.get(p).equals(Double.NaN)) {
                buyerPr.put(p, 0d);
                print("NaN replaced with 0");
            }
        }
    }

    private double Pr(boolean buyer, int p) {
        double pr;
        if (buyer) {
            // Check for obvious: doesn't beat the current outstanding bid
            if (!bidStack.isEmpty() && p < bidStack.getLast().price.intValue()) {
                pr = 0;
            } else {
                // TBLp : transaction-resulting bids at a price p or lower
                double TBLp = 0;
                for (CDAGameBid ask : acceptedBids) {
                    if (ask.price.intValue() <= p) {
                        TBLp++;
                    }
                }
                // ALp : asks at a price p or lower
                double ALp = 0;
                for (CDAGameBid bid : history.get(repetitionIndex)) {
                    if (!bid.agentIsBuyer && bid.price.intValue() <= p) {
                        ALp++;
                    }
                }
                // RBGp : unmatched bids at price p or greater
                double RBGp = 0;
                for (CDAGameBid bid : bidStack) {
                    if (bid.price.intValue() >= p) {
                        RBGp++;
                    }
                }
                // Calculate probability
                pr = (TBLp + ALp) / (TBLp + ALp + RBGp);
            }
        } else {
            // Check for obvious: doesn't beat the current outstanding ask
            if (!askStack.isEmpty() && p > askStack.getLast().price.intValue()) {
                pr = 0;
            } else {
                // TAGp : transaction-resulting asks at a price p or higher
                double TAGp = 0;
                for (CDAGameBid ask : acceptedAsks) {
                    if (ask.price.intValue() >= p) {
                        TAGp++;
                    }
                }
                // BGp : bids at a price p or higher
                double BGp = 0;
                for (CDAGameBid bid : history.get(repetitionIndex)) {
                    if (bid.agentIsBuyer && bid.price.intValue() >= p) {
                        BGp++;
                    }
                }
                // RALp : unmatched asks at price p or lower
                double RALp = 0;
                for (CDAGameBid bid : askStack) {
                    if (bid.price.intValue() <= p) {
                        RALp++;
                    }
                }
                // Calculate probability
                pr = (TAGp + BGp) / (TAGp + BGp + RALp);
            }
        }
        return pr;
    }

    private double V(int tradesLeft, int remainingBids) {
        if (V.containsKey(tradesLeft) && V.get(tradesLeft).containsKey(remainingBids)) {
            return V.get(tradesLeft).get(remainingBids);
        }
        double v;
        if (tradesLeft <= 0 || remainingBids <= 0) {
            v = 0;
        } else {
            v = Vp(tradesLeft, remainingBids, argMaxP(tradesLeft, remainingBids));
            // Save result
            if (!V.containsKey(tradesLeft)) {
                V.put(tradesLeft, new HashMap<Integer, Double>());
            }
            V.get(tradesLeft).put(remainingBids, v);
        }
        return v;
    }

    private double Pr(int p) {
        if (isBuyer()) {
            return buyerPr.get(p);
        } else {
            return sellerPr.get(p);
        }
    }

    private double s(int tradesLeft, int p) {
        int v = auctions[repetitionIndex].values.get(CDAGameConstants.MAX_TRADES - tradesLeft);
        if (isBuyer()) {
            return v - p;
        } else {
            return p - v;
        }
    }

    private double Vp(int tradesLeft, int remainingBids, int p) {
        if (Vp.containsKey(tradesLeft) && Vp.get(tradesLeft).containsKey(remainingBids) && Vp.get(tradesLeft).get(remainingBids).containsKey(p)) {
            return Vp.get(tradesLeft).get(remainingBids).get(p);
        }
        double v;
        if (tradesLeft <= 0 || remainingBids <= 0) {
            v = 0;
        } else {
            v = Pr(p) * (s(tradesLeft, p) + GAMMA * V(tradesLeft - 1, remainingBids - 1)) + (1 - Pr(p)) * GAMMA * V(tradesLeft, remainingBids - 1);
        }
        // Save result
        if (!Vp.containsKey(tradesLeft)) {
            Vp.put(tradesLeft, new HashMap<Integer, Map<Integer, Double>>());
        }
        if (!Vp.get(tradesLeft).containsKey(remainingBids)) {
            Vp.get(tradesLeft).put(remainingBids, new HashMap<Integer, Double>());
        }
        Vp.get(tradesLeft).get(remainingBids).put(p, v);
        return v;
    }

    private int argMaxP(int tradesLeft, int remainingBids) {
        if (argMaxP.containsKey(tradesLeft) && argMaxP.get(tradesLeft).containsKey(remainingBids)) {
            return argMaxP.get(tradesLeft).get(remainingBids);
        }
        int bestP;
        if (isBuyer()) {
            bestP = CDAGameConstants.V_MIN;
        } else {
            bestP = CDAGameConstants.V_MAX;
        }
        if (!(tradesLeft <= 0 || remainingBids <= 0)) {
            double bestVp = 0;
            if (isBuyer()) {
                for (int p = auctions[repetitionIndex].currentValue().intValue(); p >= CDAGameConstants.V_MIN; p--) {
                    double thisVp = Vp(tradesLeft, remainingBids, p);
                    if (thisVp > bestVp) {
                        bestP = p;
                        bestVp = thisVp;
                    }
                }
            } else {
                for (int p = auctions[repetitionIndex].currentValue().intValue(); p <= CDAGameConstants.V_MAX; p++) {
                    double thisVp = Vp(tradesLeft, remainingBids, p);
                    if (thisVp > bestVp) {
                        bestP = p;
                        bestVp = thisVp;
                    }
                }
            }
        }
        // Save result
        if (!argMaxP.containsKey(tradesLeft)) {
            argMaxP.put(tradesLeft, new HashMap<Integer, Integer>());
        }
        argMaxP.get(tradesLeft).put(tradesLeft, bestP);
        return bestP;
    }

    @Override
    public void submitBids() {
        // Clear off the DP tables
        V.clear();
        Vp.clear();
        argMaxP.clear();
        // Which price maximizes expected surplus?
        int tradesLeft = CDAGameConstants.MAX_TRADES - auctions[repetitionIndex].holdings;
        long repetitionStartTime = startTime.longValue() + repetitionIndex * CDAGameConstants.AUCTION_LEN;
        long timeLeft = CDAGameConstants.AUCTION_LEN - CDAGameConstants.AUCTION_BUFFER_LEN - (now().longValue() - repetitionStartTime);
        int remainingBids = (int) Math.floor(((double) timeLeft / (double) CDAGameConstants.BID_SLEEP_TIME) - 1d);
        print("Trades remaining: " + tradesLeft + ", Bids remaining: " + remainingBids);
        int bid = argMaxP(tradesLeft, remainingBids);
        print("Expected value: " + Vp(tradesLeft, remainingBids, bid));
        print("Pr: " + Pr(bid));
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
        // Submit the bid
        if (bid >= 0) {
            submitBid(bid);
        }
    }
}
