package sim.cda;

import ab3d.agent.Agent;
import ab3d.auction.Bid;
import ab3d.auction.PQBid;
import ab3d.auction.Price;
import ab3d.auction.Transaction;
import ab3d.comm.TACProtocol;
import ab3d.util.Log;
import ab3d.util.MySystem;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.jdom.Element;

/**
 *
 * @author Augie
 */
public abstract class CDAGameAgent extends Agent implements Runnable {

    protected String logID;
    protected CDAGameParams gameParams;
    protected CDAGameAgentAuctionData[] auctions;
    protected int repetitionIndex = 0;

    public CDAGameAgent(String host, int port, String agentID, String agentPW) {
        super(host, port, agentID, agentPW);
    }

    public CDAGameAgent(String host, int port, String agentID, String agentPW, Log l) {
        super(host, port, agentID, agentPW, l);
    }

    public CDAGameAgent() {
        super();
    }

    public boolean isBuyer() {
        synchronized (Main.BUYERS) {
            return Main.BUYERS.contains(agentName);
        }
    }

    protected final void log(String strat, String method, String msg) {
        log.log(Log.INFO, strat + "::" + method + ":  " + msg);
    }

    public int readGameParams(Integer gameID) {
        Element e = getGameParams(gameID);
        try {
            if (e.getChild("auctionValues") != null) {
                gameParams = new CDAGameParams(e);
                gameParams.sortValues(isBuyer());
            } else {
                return 0;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            commandStatus = _BAD_MESSAGE;
            return 0;
        }
        return 1;
    }

    protected final void readParams() {
        // Read the auction IDs
        log(logID, "readParams", "Starting");

        // Read the auction IDs
        List<Integer> auctionIDs = new LinkedList<Integer>();
        for (Object o : getGameAuctionIDs()) {
            Element elmnt = (Element) o;
            try {
                auctionIDs.add(Integer.valueOf(elmnt.getText()));
            } catch (NumberFormatException e) {
                deb("invalid message");
                commandStatus = _BAD_MESSAGE;
                return;
            }
        }

        // Also read the game params
        readGameParams(gameID);

        // Create the auction data structures
        auctions = new CDAGameAgentAuctionData[CDAGameConstants.AUCTION_REPETITIONS];
        for (int i = 0; i < CDAGameConstants.AUCTION_REPETITIONS; i++) {
            auctions[i] = new CDAGameAgentAuctionData();
            auctions[i].id = auctionIDs.get(0);
            auctions[i].values = gameParams.values;
        }
    }

    @Override
    public void run() {
        try {
            log(logID, "run", "Starting");

            // Wait for the next games to begin
            while (!nextGame()) {
                log(logID, "run", "Waiting for next game");
                MySystem.sleep(1);
            }

            // Something went wrong, exit
            if ((gameID == null) && (gameID.intValue() == -1)) {
                log(logID, "run", "Bad game ID value (" + gameID + ")");
                return;
            }

            // Read game information from the server and wait for the game to start
            readParams();
            log(logID, "run", "Waiting for the game to begin at " + getStartTime().longValue());
            sleep(getStartTime().longValue());

            // Repeat the circumstances of the auction some number of times
            REPETITIONS:
            for (; repetitionIndex < CDAGameConstants.AUCTION_REPETITIONS; repetitionIndex++) {
                // Wait for the repetition bidding time start
                if (repetitionIndex > 0) {
                    sleep(getStartTime().longValue() + repetitionIndex * CDAGameConstants.AUCTION_LEN);
                }
                print("");
                print("Round " + repetitionIndex);
                // How many times will the agent be able to bid?
                int bids = (int) Math.floor(((double) (CDAGameConstants.AUCTION_LEN - CDAGameConstants.AUCTION_BUFFER_LEN) / (double) CDAGameConstants.BID_SLEEP_TIME) - 1d);
                for (int bid = 0; bid < bids; bid++) {
                    try {
                        synchronized (Utils.BID_LOCK) {
                            // Update auction state
                            if (updateAuction() == 0) {
                                break REPETITIONS;
                            }
                            // Adjust the agent's state based upon the the updated auction
                            adjustState();
                            // Has this agent made all possible trades?
                            if (auctions[repetitionIndex].holdings >= CDAGameConstants.MAX_TRADES) {
                                continue;
                            }
                            // Check for time to double check that the bid is being submitted to the correct auction
                            long repEndTime = getStartTime().longValue() + (repetitionIndex + 1) * CDAGameConstants.AUCTION_LEN;
                            if (now().longValue() > repEndTime - CDAGameConstants.AUCTION_BUFFER_LEN * 0.5) {
                                Main.printLog("Stopped late bid submission (by " + (now().longValue() - repEndTime + CDAGameConstants.AUCTION_BUFFER_LEN * 0.5) + ") .");
                                break;
                            }
                            // What is the ID of the current bid?
                            PQBid oldBid = null;
                            if (auctions[repetitionIndex].currentBid() != null) {
                                oldBid = auctions[repetitionIndex].currentBid();
                            }
                            // Calculate and submit a bid
                            submitBids();
                            // Make the same bid if did not make a new bid this round
                            if (auctions[repetitionIndex].currentBid() == null) {
                                print("Current bid is null, submitting default bid.");
                                if (isBuyer()) {
                                    submitBid(CDAGameConstants.V_MIN);
                                } else {
                                    submitBid(CDAGameConstants.V_MAX);
                                }
                            } else if (oldBid != null && oldBid.toXMLString().equals(auctions[repetitionIndex].currentBid().toXMLString())) {
                                print("Old bid and current bid are equivalent, so resubmitting bid price.");
                                submitBid(auctions[repetitionIndex].currentBid().bidArray[0].getprice().intValue());
                            }
                        }
                        // Wait a little longer if this is the last bid (to update transactions)
                        if (bid == bids - 1) {
                            try {
                                Thread.sleep((int) (CDAGameConstants.AUCTION_BUFFER_LEN * 0.5));
                            } catch (Exception e) {
                            }
                        }
                        synchronized (Utils.BID_LOCK) {
                            // See if any transactions occurred as a result of this bid
                            updateTransactions();
                        }
                    } catch (Exception e) {
                        try {
                            Main.printLog("Error: " + e.getMessage());
                        } catch (Exception ee) {
                        }
                    } finally {
                        // Wait for the next time to bid
                        if (bid < bids - 1) {
                            long wakeTime = getStartTime().longValue() + repetitionIndex * CDAGameConstants.AUCTION_LEN + (bid + 1) * CDAGameConstants.BID_SLEEP_TIME - (long) (Utils.RANDOM.nextDouble() * CDAGameConstants.BID_SLEEP_TIME * 0.25);
                            if (wakeTime > now().longValue()) {
                                sleep(wakeTime);
                            } else {
                                try {
                                    Thread.sleep(1);
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                }
            }
            // Wait and update the last auction one more time to get the transactions
            try {
                Thread.sleep((long) (CDAGameConstants.AUCTION_BUFFER_LEN * 0.5));
            } catch (Exception e) {
            }
            // Update auction state (to get transactions)
            repetitionIndex = CDAGameConstants.AUCTION_REPETITIONS - 1;
            updateAuction();
        } finally {
            // All done, bye bye
            log(logID, "run", "Exiting");
        }
    }

    @Override
    public void stop() {
    }

    protected final PQBid submitBid(int bid) {
        // Make sure no bids are made after 10 units have been traded
        if (auctions[repetitionIndex].currentValue() == null) {
            return null;
        }
        // Make sure the bid is not generating negative utility
        if ((isBuyer() && bid > auctions[repetitionIndex].currentValue().intValue())
                || (!isBuyer() && bid < auctions[repetitionIndex].currentValue().intValue())) {
            Main.printLog("Bad bid by agent " + agentID + ". (Bid " + bid + ", Value " + auctions[repetitionIndex].currentValue().intValue() + ", Buyer " + Main.isBuyer(agentName) + ")");
            return null;
        }
        // Dummy check
        if (bid > CDAGameConstants.V_MAX) {
            bid = CDAGameConstants.V_MAX;
        } else if (bid < CDAGameConstants.V_MIN) {
            bid = CDAGameConstants.V_MIN;
        }

        // Generate the new bid, remember the old
        PQBid oldBid = auctions[repetitionIndex].currentBid();
        PQBid newBid = new PQBid();

        // Buyers want to acquire a unit, sellers want to sell a unit
        newBid.addPoint(isBuyer() ? 1 : -1, new Price(bid));
        log(logID, "submitBid", "Bid placed to auction #" + auctions[repetitionIndex].id + " (" + newBid.getBidString() + ")");

        // Recall that all bids are synchronized, so there won't be an issue with this shared memory
        CDAGameBid logBid = new CDAGameBid();
        logBid.price = bid;
        logBid.timestamp = now();
        logBid.agentIsBuyer = isBuyer();
        logBid.agentID = agentID;
        Utils.addBid(repetitionIndex, logBid);

        // Submit the bid to the auction
        Bid responseBid;
        if (oldBid == null) {
            print("Submitting new bid: " + bid + " (Value " + auctions[repetitionIndex].currentValue().intValue() + ")");
            responseBid = submitBid(newBid, auctions[repetitionIndex].id);
        } else {
            print("Replacing bid: " + bid + " (Value " + auctions[repetitionIndex].currentValue().intValue() + ")");
            responseBid = submitBid(newBid, auctions[repetitionIndex].id);
        }

        // Check for error
        print(responseBid.toXMLString());
        if (responseBid.rejectReason == null) {
            print("Bid rejected.");
        } else if (responseBid.rejectReason.intValue() != 0) {
            print("Bid rejected: " + responseBid.rejectReason.intValue());
        }

        // Log this bid
        newBid.bid_id = responseBid.bid_id;
        auctions[repetitionIndex].setBid(newBid);

        return newBid;
    }

    public void adjustState() {
    }

    public abstract void submitBids();

    public void updateTransactions() {
        if (!auctions[repetitionIndex].closed) {
            // Update holdings
            Vector newTransactions = getNewTransactions();
            if (newTransactions != null) {
                print("Transactions: " + newTransactions.size());
                for (Object o : newTransactions) {
                    Transaction transaction = (Transaction) o;
                    // To which repetition does this transaction belong?
                    int belongingRepetition = repetitionIndex;
                    if (repetitionIndex > 0 && transaction.timestamp.longValue() < getStartTime().longValue() + repetitionIndex * CDAGameConstants.AUCTION_LEN) {
                        belongingRepetition = repetitionIndex - 1;
                    }
                    print("Belonging repetition: " + belongingRepetition);
                    // Log transaction
                    if (!Utils.TRANSACTIONS.containsKey(belongingRepetition)) {
                        Utils.TRANSACTIONS.put(belongingRepetition, new ArrayList<Transaction>());
                    }
                    // Is this a duplicate transaction? (there are 2 reports per transaction)
                    boolean dupe = false;
                    for (Transaction t : Utils.TRANSACTIONS.get(belongingRepetition)) {
                        if (transaction.price.intValue() == t.price.intValue()
                                && transaction.timestamp.longValue() == t.timestamp.longValue()
                                && transaction.buyerID.intValue() == t.buyerID.intValue()
                                && transaction.sellerID.intValue() == t.sellerID.intValue()) {
                            dupe = true;
                        }
                    }
                    if (!dupe) {
                        Utils.TRANSACTIONS.get(belongingRepetition).add(transaction);
                    }
                    // Update holdings
                    if (transaction.buyerID.intValue() == agentID.intValue() || transaction.sellerID.intValue() == agentID.intValue()) {
                        if (belongingRepetition == repetitionIndex) {
                            print("Transaction occurred.");
                        } else {
                            print("Transaction occurred (at end of last round).");
                        }
                        if (auctions[belongingRepetition].holdings < CDAGameConstants.MAX_TRADES) {
                            auctions[belongingRepetition].holdings++;
                        }
                    }
                }
            }
        }
    }

    public int updateAuction() {
        int open = 0;
        if (!auctions[repetitionIndex].closed) {
            // Update holdings
            Vector newTransactions = getNewTransactions();
            if (newTransactions != null) {
                print("Transactions: " + newTransactions.size());
                for (Object o : newTransactions) {
                    Transaction transaction = (Transaction) o;
                    // To which repetition does this transaction belong?
                    int belongingRepetition = repetitionIndex;
                    if (repetitionIndex > 0 && transaction.timestamp.longValue() < getStartTime().longValue() + repetitionIndex * CDAGameConstants.AUCTION_LEN) {
                        belongingRepetition = repetitionIndex - 1;
                    }
                    print("Belonging repetition: " + belongingRepetition);
                    // Log transaction
                    if (!Utils.TRANSACTIONS.containsKey(belongingRepetition)) {
                        Utils.TRANSACTIONS.put(belongingRepetition, new ArrayList<Transaction>());
                    }
                    // Is this a duplicate transaction? (there are 2 reports per transaction)
                    boolean dupe = false;
                    for (Transaction t : Utils.TRANSACTIONS.get(belongingRepetition)) {
                        if (transaction.price.intValue() == t.price.intValue()
                                && transaction.timestamp.longValue() == t.timestamp.longValue()
                                && transaction.buyerID.intValue() == t.buyerID.intValue()
                                && transaction.sellerID.intValue() == t.sellerID.intValue()) {
                            dupe = true;
                        }
                    }
                    if (!dupe) {
                        Utils.TRANSACTIONS.get(belongingRepetition).add(transaction);
                    }
                    // Update holdings
                    if (transaction.buyerID.intValue() == agentID.intValue() || transaction.sellerID.intValue() == agentID.intValue()) {
                        if (belongingRepetition == repetitionIndex) {
                            print("Transaction occurred.");
                        } else {
                            print("Transaction occurred (at end of last round).");
                        }
                        if (auctions[belongingRepetition].holdings < CDAGameConstants.MAX_TRADES) {
                            auctions[belongingRepetition].holdings++;
                        }
                    }
                }
            }
            // Update quote
            auctions[repetitionIndex].quote = getQuote(null, auctions[repetitionIndex].id);
            if (auctions[repetitionIndex].quote == null || auctions[repetitionIndex].quote.as == null || auctions[repetitionIndex].quote.as.intValue() == TACProtocol.AS_AUCTION_CLOSED) {
                auctions[repetitionIndex].closed = true;
                print("Auction closed.");
            } else {
                open = 1;
            }
        }
        log(Log.INFO, "auction status: " + (auctions[repetitionIndex].closed ? "closed" : "open"));
        return open;
    }

    protected void print(String msg) {
//        if (agentName.equals("aa1")) {
//            System.out.println(msg);
//        }
    }
}
