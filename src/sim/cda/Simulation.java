package sim.cda;

import ab3d.util.Log;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

/**
 *
 * @author Augie <augie@umich.edu>
 */
public class Simulation {

    public final int id;
    public final File resultsDir;
    public final Map<String, Collection<Object>> profile;
    public final Map<String, String> params;

    public Simulation(int id, File resultsDir, Map<String, Collection<Object>> profile, Map<String, String> params) {
        this.id = id;
        this.resultsDir = resultsDir;
        this.profile = profile;
        this.params = params;
    }

    public SimulationResults run() throws Exception {
        // Reset the market information for this simulation
        Utils.reset();
        GD.reset();
        GDX.reset();
        
        // Who is playing?
        StringBuilder players = new StringBuilder();
        boolean first = true;
        for (Object o : profile.get("Sellers")) {
            if (!first) {
                players.append(":");
            } else {
                first = false;
            }
            players.append(Main.AGENTS.get((String) o));
        }
        for (Object o : profile.get("Buyers")) {
            players.append(":");
            players.append(Main.AGENTS.get((String) o));
        }

        // Start the game
        Utils.send("<createGame><gameFile>game/game.xml</gameFile><players>" + players.toString() + "</players><startTime>" + System.currentTimeMillis() + "</startTime></createGame>", Main.GAME_SCHEDULER);

        // Create and start the bidding agent threads
        List<Thread> agentThreads = new LinkedList<Thread>();
        Log log = new Log(Log.NO_LOGGING, resultsDir.getAbsolutePath(), "agents" + id + ".log", true);
        for (Object o : profile.get("Sellers")) {
            agentThreads.add(createPlayer((String) o, log));
        }
        for (Object o : profile.get("Buyers")) {
            agentThreads.add(createPlayer((String) o, log));
        }
        Collections.shuffle(agentThreads);
        for (Thread t : agentThreads) {
            t.start();
        }

        // Wait for the game to run and get cleaned up
        Thread.sleep(CDAGameConstants.AUCTION_LEN * CDAGameConstants.AUCTION_REPETITIONS + 10000);

        // Kill the bidding agents
        for (Thread agentThread : agentThreads) {
            try {
                agentThread.interrupt();
            } catch (Exception e) {
            }
        }

        // Return results
        SimulationResults results = new SimulationResults();

        // Open results file
        File allocFile = new File(resultsDir, id + "/alloc.xml");
        if (!allocFile.exists()) {
            return results;
        }

        // Parse results file
        Builder parser = new Builder();
        Document allocDoc = parser.build(allocFile);
        Element resultsEl = allocDoc.getRootElement();
        Element scoresEl = resultsEl.getFirstChildElement("scores");
        Elements agentEls = scoresEl.getChildElements("agent");
        // Maps <User name, Score>
        Map<String, String> scores = new HashMap<String, String>();
        for (int i = 0; i < agentEls.size(); i++) {
            Element agentEl = agentEls.get(i);
            Element userNameEl = agentEl.getFirstChildElement("userName");
            Element scoreEl = agentEl.getFirstChildElement("score");
            scores.put(userNameEl.getValue(), scoreEl.getValue());
        }

        // Average results over the controlled agents
        for (String role : profile.keySet()) {
            Map<String, Double> userCount = new HashMap<String, Double>();
            results.payoffs.put(role, new HashMap<String, Object>());
            for (String userName : profile.get(role).toArray(new String[0])) {
                String strategy = Main.getStrategy(userName);
                if (!userCount.containsKey(strategy)) {
                    userCount.put(strategy, 0d);
                }
                userCount.put(strategy, userCount.get(strategy).doubleValue() + 1d);
                if (!results.payoffs.get(role).containsKey(strategy)) {
                    results.payoffs.get(role).put(strategy, 0d);
                }
                double score = Double.valueOf(scores.get(userName)).doubleValue();
                results.payoffs.get(role).put(strategy, ((Double) results.payoffs.get(role).get(strategy)).doubleValue() + score);
            }
            // Average the results
            for (String strat : results.payoffs.get(role).keySet()) {
                results.payoffs.get(role).put(strat, ((Double) results.payoffs.get(role).get(strat)).doubleValue() / userCount.get(strat).doubleValue());
            }
        }

        // Dummy feature
        results.features.put("timestamp", System.currentTimeMillis());

        return results;
    }

    private Thread createPlayer(final String player, final Log log) {
        Thread agentThread = new Thread() {

            @Override
            public void run() {
                CDAGameAgent agent = null;
                if (player.startsWith("zip")) {
                    agent = new ZIP(Main.HOST, Main.AGENT_MANAGER, player, player, log);
                } else if (player.startsWith("zibtq")) {
                    agent = new ZIBTQ(Main.HOST, Main.AGENT_MANAGER, player, player, log);
                } else if (player.startsWith("zi")) {
                    agent = new ZI(Main.HOST, Main.AGENT_MANAGER, player, player, log);
                } else if (player.startsWith("rb")) {
                    agent = new RB(Main.HOST, Main.AGENT_MANAGER, player, player, log);
                } else if (player.startsWith("kaplan")) {
                    agent = new KAPLAN(Main.HOST, Main.AGENT_MANAGER, player, player, log);
                } else if (player.startsWith("gdx")) {
                    agent = new GDX(Main.HOST, Main.AGENT_MANAGER, player, player, log);
                } else if (player.startsWith("gd")) {
                    agent = new GD(Main.HOST, Main.AGENT_MANAGER, player, player, log);
                } else if (player.startsWith("aa")) {
                    agent = new AA(Main.HOST, Main.AGENT_MANAGER, player, player, log);
                }
                agent.run();
            }
        };
        agentThread.setDaemon(true);
        return agentThread;
    }
}
