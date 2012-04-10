package sim.cda;

import ab3d.util.GameParams;
import ab3d.util.InvalidXMLException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.jdom.Element;

/**
 *
 * @author Augie
 */
public class CDAGameParams extends GameParams {

    // Private values
    public List<Integer> values = new LinkedList<Integer>();

    public CDAGameParams(Element elmnt) throws InvalidXMLException {
        readXMLFields(elmnt);
    }

    public void sortValues(boolean isBuyer) {
        // Sorts values in ascending order
        Collections.sort(values);
        // Reverses the order if the agent is a buyer
        //  This causes half of the goods to be sold in expectation
        if (isBuyer) {
            Collections.reverse(values);
        }
    }

    @Override
    protected void readXMLFields(Element elmnt) throws InvalidXMLException {
//        XMLOutputter out = new XMLOutputter();
//        try {
//            out.output(elmnt, System.out);
//            System.out.println();
//        } catch (Exception e) {
//        }
        Element temp;
        if (((temp = elmnt.getChild("auctionValues")) != null)) {
            if (temp.getChildren("auction") != null) {
                for (Object auctionO : temp.getChildren("auction")) {
                    Element auctionE = (Element) auctionO;
                    for (Object valueO : auctionE.getChildren("value")) {
                        Element valueEl = (Element) valueO;
                        values.add(Integer.valueOf(valueEl.getText()));
                    }
                }
            }
        }
    }

    @Override
    protected void setMembersNull() {
    }

    @Override
    public Element toXMLTree() {
        return null;
    }
}
