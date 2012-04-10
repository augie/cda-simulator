package sim.cda;

import ab3d.auction.Price;
import ab3d.systemmanager.Scorer;
import ab3d.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

/**
 * 
 * @author Augie <augie@umich.edu>
 */
public class CDAGameScorer extends Scorer {

    // Maps <agent ID, <private value>>
    private Map<Integer, List<Integer>> agentValues = new HashMap<Integer, List<Integer>>();
    // Maps auction index to single unit auction scoring data
    private CDAGameAuctionScore[] repetitions = new CDAGameAuctionScore[CDAGameConstants.AUCTION_REPETITIONS];

    @Override
    public int generateScore(String logFilename, String xmlFilename) {
        // Clear the data structures
        init();

        // Read the logs
        if (readLog(logFilename) != 0) {
            log(Log.ERROR, "CDAGameScorer::generateScore:  Error reading log file: " + logFilename);
            return -1;
        }

        // Calculate a score for each agent
        Element results = new Element("results");

        // Report the transactions that occurred
        Element transactions = new Element("transactions");
        results.addContent(transactions);
        for (int i = 0; i < repetitions.length; i++) {
            if (repetitions[i] != null) {

                // Sort transactions by timestamp
                Collections.sort(repetitions[i].transactions, new Comparator<CDAGameAuctionScoreTransaction>() {

                    @Override
                    public int compare(CDAGameAuctionScoreTransaction o1, CDAGameAuctionScoreTransaction o2) {
                        return o1.transactionTimestamp.compareTo(o2.transactionTimestamp);
                    }
                });

                Element repetition = new Element("repetition");
                transactions.addContent(repetition);
                repetition.addContent(new Element("id").setText(String.valueOf(i + 1)));
                for (CDAGameAuctionScoreTransaction t : repetitions[i].transactions) {
                    Element transaction = new Element("transaction");
                    repetition.addContent(transaction);
                    transaction.addContent(new Element("timestamp").setText(String.valueOf(t.transactionTimestamp.longValue())));
                    transaction.addContent(new Element("buyerID").setText(String.valueOf(t.buyerID.intValue())));
                    transaction.addContent(new Element("sellerID").setText(String.valueOf(t.sellerID.intValue())));
                    transaction.addContent(new Element("price").setText(String.valueOf(t.transactionPrice.intValue())));
                }
            }
        }

        // Calculate scores
        Element scores = new Element("scores");
        results.addContent(scores);
        for (int aID : agentValues.keySet()) {
            double sum = 0;
            for (int i = 0; i < repetitions.length; i++) {
                if (repetitions[i] == null) {
                    continue;
                }
                int valueIndex = 0;
                for (CDAGameAuctionScoreTransaction t : repetitions[i].transactions) {
                    if (Main.isBuyer(Main.getAgent(String.valueOf(aID)))) {
                        if (t.buyerID.intValue() == aID) {
                            if (valueIndex >= CDAGameConstants.MAX_TRADES) {
                                continue;
                            }
                            sum += agentValues.get(aID).get(valueIndex++).intValue() - t.transactionPrice.intValue();
                        }
                    } else {
                        if (t.sellerID.intValue() == aID) {
                            if (valueIndex >= CDAGameConstants.MAX_TRADES) {
                                continue;
                            }
                            sum += t.transactionPrice.intValue() - agentValues.get(aID).get(valueIndex++).intValue();
                        }
                    }
                }
            }
            double score = sum / (double) CDAGameConstants.AUCTION_REPETITIONS;
            Element agentElem = new Element("agent");
            agentElem.addContent(new Element("ID").setText(String.valueOf(aID)));
            String userName = Main.getAgent(String.valueOf(aID));
            agentElem.addContent(new Element("userName").setText(userName));
            agentElem.addContent(new Element("strategy").setText(Main.getStrategy(userName)));
            Element agentValuesElem = new Element("values");
            agentElem.addContent(agentValuesElem);
            for (int value : agentValues.get(aID)) {
                agentValuesElem.addContent(new Element("value").setText(String.valueOf(value)));
            }
            agentElem.addContent(new Element("score").setText(String.valueOf(score)));
            scores.addContent(agentElem);
        }

        // Write out the results document
        Document doc = new Document(results);
        FileWriter fw = null;
        try {
            fw = new FileWriter(new File(xmlFilename));
            PrintWriter out = new PrintWriter(fw);
            XMLOutputter serializer = new XMLOutputter();
            serializer.setOmitDeclaration(true);
            serializer.setNewlines(true);
            serializer.setIndent("  ");
            serializer.output(doc, out);
        } catch (IOException e) {
            Utils.safeClose(fw);
            return -1;
        }

        return 0;
    }

    @Override
    protected int readAuctionID(Element em) {
        return 0;
    }

    @Override
    protected int readCommitInfo(Element em) {
        return 0;
    }

    @Override
    protected int readEvents(Element em) {
        return 0;
    }

    @Override
    protected int readFinalTrans(Element em) {
//        XMLOutputter out = new XMLOutputter();
//        try {
//            out.output(em, System.out);
//            System.out.println();
//        } catch (Exception e) {
//        }
        Element buyListEl = em.getChild("buy").getChild("list");
        if (buyListEl != null) {
            for (Object transTupleO : buyListEl.getChildren("transTuple")) {
                try {
                    Element transTupleEl = (Element) transTupleO;
                    Long thisTransactionTimestamp = Long.valueOf(transTupleEl.getChildText("timestamp"));

                    // Find the auction repetition to which this transaction belongs
                    int repetition = -1;
                    for (int thisRepetition = 0; thisRepetition < CDAGameConstants.AUCTION_REPETITIONS; thisRepetition++) {
                        if (thisTransactionTimestamp.longValue() > m_startTime.longValue() + thisRepetition * CDAGameConstants.AUCTION_LEN && thisTransactionTimestamp.longValue() <= m_startTime.longValue() + (thisRepetition + 1) * CDAGameConstants.AUCTION_LEN) {
                            repetition = thisRepetition;
                            break;
                        }
                    }
                    if (repetition == -1) {
                        Main.printLog("Wat? Auction: " + m_startTime.longValue() + " - " + (m_startTime.longValue() + CDAGameConstants.AUCTION_REPETITIONS * CDAGameConstants.AUCTION_LEN) + ", Transaction: " + thisTransactionTimestamp.longValue());
                        Main.printLog("Before? " + (thisTransactionTimestamp.longValue() < m_startTime.longValue()));
                        Main.printLog("After? " + (thisTransactionTimestamp.longValue() > m_startTime.longValue() + CDAGameConstants.AUCTION_REPETITIONS * CDAGameConstants.AUCTION_LEN));
                        if (thisTransactionTimestamp.longValue() < m_startTime.longValue()) {
                            repetition = 0;
                        } else {
                            repetition = CDAGameConstants.AUCTION_REPETITIONS - 1;
                        }
                    }

                    // Create the auction score if it does not exist
                    if (repetitions[repetition] == null) {
                        repetitions[repetition] = new CDAGameAuctionScore();
                    }

                    CDAGameAuctionScoreTransaction t = new CDAGameAuctionScoreTransaction();
                    t.transactionTimestamp = thisTransactionTimestamp;
                    t.sellerID = Integer.valueOf(transTupleEl.getChildText("sellerID"));
                    t.buyerID = Integer.valueOf(transTupleEl.getChildText("buyerID"));
                    t.transactionPrice = new Price(transTupleEl.getChildText("price"));
                    repetitions[repetition].transactions.add(t);
                } catch (Exception e) {
                }
            }
        }
        return 0;
    }

    @Override
    protected int readGameParam(Element em) {
//        XMLOutputter out = new XMLOutputter();
//        try {
//            out.output(em, System.out);
//            System.out.println();
//        } catch (Exception e) {
//        }
        Integer agentID = Integer.valueOf(em.getChildText("agent"));
        agentValues.put(agentID, new LinkedList<Integer>());
        for (Object auctionO : em.getChild("auctionValues").getChildren("auction")) {
            Element auctionE = (Element) auctionO;
            for (Object valueO : auctionE.getChildren("value")) {
                Element valueE = (Element) valueO;
                agentValues.get(agentID).add(Integer.valueOf(valueE.getText()));
            }
        }
        // Sort agent values
        Collections.sort(agentValues.get(agentID));
        // Reverse if it's a buyer
        if (Main.isBuyer(Main.getAgent(String.valueOf(agentID)))) {
            Collections.reverse(agentValues.get(agentID));
        }
        return 0;
    }

    @Override
    protected int readTrans(Element em) {
        return 0;
    }
}
