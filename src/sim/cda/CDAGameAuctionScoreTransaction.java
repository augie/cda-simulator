package sim.cda;

import ab3d.auction.Price;

/**
 *
 * @author Augie
 */
public class CDAGameAuctionScoreTransaction {
    
    // The price at which the good was sold
    public Price transactionPrice;
    // The ID of the agents involved in the transaction
    public Integer buyerID, sellerID;
    // The timestamp at which the earliest transaction occurred
    public Long transactionTimestamp;
}
