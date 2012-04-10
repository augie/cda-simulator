package sim.cda;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Augie <augie@umich.edu>
 */
public class SimulationResults {

    public final Map<String, Map<String, Object>> payoffs = new HashMap<String, Map<String, Object>>();
    public final Map<String, Object> features = new HashMap<String, Object>();
}
