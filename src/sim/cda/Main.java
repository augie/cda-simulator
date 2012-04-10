package sim.cda;

import ab3d.auctionsupervisor.AuctionSupervisor;
import ab3d.systemmanager.SystemManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.ho.yaml.Yaml;

/**
 * 
 * @author Augie <augie@umich.edu>
 */
public class Main {

    private static PrintStream LOG_PRINTSTREAM;
    // User names to IDs and IDs to user names
    public static final Map<String, String> AGENTS = new HashMap<String, String>();
    // Strategy to the number of players playing that strategy
    public static final Map<String, Integer> STRATEGY_PLAYER_INDICES = new HashMap<String, Integer>();
    // Buyer user names
    public static final Set<String> BUYERS = new HashSet<String>();
    public static final String HOST = "localhost";
    public static final int AGENT_MANAGER = 7000;
    public static final int GAME_SCHEDULER = 7001;
    public static final int SYSTEM_CACHE = 7002;
    public static Map<String, Collection<Object>> SIM_PROFILE = null;

    static {
        String[] strategies = new String[]{"zi", "zibtq", "zip", "kaplan", "gd", "gdx", "rb", "aa"};
        for (int s = 0; s < 8; s++) {
            String strategy = strategies[s];
            for (int i = 0; i < 16; i++) {
                AGENTS.put(strategy + (i + 1), String.valueOf(s * 20 + i + 11));
            }
            STRATEGY_PLAYER_INDICES.put(strategy, 0);
        }
    }

    public static String getStrategy(String player) {
        if (player.startsWith("zip")) {
            return "ZIP";
        } else if (player.startsWith("zibtq")) {
            return "ZIBTQ";
        } else if (player.startsWith("zi")) {
            return "ZI";
        } else if (player.startsWith("rb")) {
            return "RB";
        } else if (player.startsWith("kaplan")) {
            return "KAPLAN";
        } else if (player.startsWith("gdx")) {
            return "GDX";
        } else if (player.startsWith("gd")) {
            return "GD";
        } else if (player.startsWith("aa")) {
            return "AA";
        }
        return null;
    }

    public static String getAgent(String nameOrID) {
        if (!AGENTS.containsKey(nameOrID)) {
            if (AGENTS.containsValue(nameOrID)) {
                for (String name : AGENTS.keySet()) {
                    String id = AGENTS.get(name);
                    if (id.equals(nameOrID)) {
                        AGENTS.put(id, name);
                        return name;
                    }
                }
            }
        }
        return AGENTS.get(nameOrID);
    }

    public static boolean isBuyer(String agent) {
        if (SIM_PROFILE == null) {
            return false;
        }
        return BUYERS.contains(agent);
    }

    public static String getNextAgentForStrategy(String strategy) {
        int nextStrategyID = STRATEGY_PLAYER_INDICES.get(strategy) + 1;
        STRATEGY_PLAYER_INDICES.put(strategy, nextStrategyID);
        if (nextStrategyID >= 17) {
            throw new RuntimeException("Wat?");
        }
        return strategy + nextStrategyID;
    }

    public static void printLog(String msg) {
        if (LOG_PRINTSTREAM != null) {
            LOG_PRINTSTREAM.println(System.currentTimeMillis() + ": " + msg);
            LOG_PRINTSTREAM.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        // Expected inputs: [simulation folder] [number of samples to gather]
        if (args.length != 2) {
            throw new RuntimeException("Expected 2 arguments.");
        }

        // Gather inputs
        String simDirLoc = args[0];
        int samples = Integer.valueOf(args[1]);
        Main.printLog("Simulation directory: " + simDirLoc);
        Main.printLog("Samples: " + samples);

        // Open sim dir
        File simDir = new File(simDirLoc);
        if (!simDir.exists()) {
            throw new RuntimeException("Simulation directory does not exist: " + simDir.getAbsolutePath());
        }

        // Create a file to log what's happening in the simulation
        File logFile = new File(simDir, "sim.log");
        if (logFile.exists() || (!logFile.exists() && logFile.createNewFile())) {
            LOG_PRINTSTREAM = new PrintStream(new FileOutputStream(logFile));
        }

        // Dummy check
        if (samples <= 0) {
            printLog("No samples requested: " + samples);
            return;
        }

        // Open features dir - the place where your simulator should log feature information
        File featuresDir = new File(simDir, "features");
        if (!featuresDir.exists() && !featuresDir.mkdir()) {
            throw new RuntimeException("Could not create directory: " + featuresDir.getAbsolutePath());
        }

        // Open payoff data dir - where the web interface will look for your logged payoff information
        File payoffFile = new File(simDir, "payoff_data");
        if (!payoffFile.exists() && !payoffFile.createNewFile()) {
            throw new RuntimeException("Could not create file: " + payoffFile.getAbsolutePath());
        }

        // Open simulation spec
        File simSpecFile = new File(simDir, "simulation_spec.yaml");
        if (!simSpecFile.exists()) {
            throw new RuntimeException("Could not find simulation spec: " + simSpecFile.getAbsolutePath());
        }

        // Read sim spec
        String simSpec = FileUtils.readFileToString(simSpecFile);

        // Two sections: strategies and params
        String[] simSpecSplit = simSpec.split("---");
        String stratSpec = simSpecSplit[1];
        String paramSpec = "---\n" + simSpecSplit[2];

        // Read simulation spec
        SIM_PROFILE = new HashMap<String, Collection<Object>>();
        // Parse stratSpec into SIM_PROFILE
        BufferedReader reader = new BufferedReader(new StringReader(stratSpec));
        List<String> stratSpecLines = new LinkedList<String>();
        String temp;
        while ((temp = reader.readLine()) != null) {
            if (!temp.trim().equals("")) {
                stratSpecLines.add(temp);
            }
        }
        if (stratSpecLines.size() != 5) {
            throw new Exception("Expecting 4 strategies");
        }
        List allStrategies = new LinkedList();
        for (int i = 1; i <= 4; i++) {
            allStrategies.add(stratSpecLines.get(i).replace("-", "").trim());
        }
        SIM_PROFILE.put("ALL", allStrategies);

        // Read parameters
        Map<String, String> simParams = (Map<String, String>) Yaml.load(paramSpec);

        // Create the config directory
        File configDir = new File(simDir, "config");
        if (!configDir.exists() && !configDir.mkdir()) {
            throw new RuntimeException("Could not create directory: " + configDir.getAbsolutePath());
        }

        // Read the config file
        String config = IOUtils.toString(Simulation.class.getResourceAsStream("/sim/cda/ab3d/config/ab3d.conf"));
        // Set the file locations properly in config file
        config = config.replace("../", simDir.getAbsolutePath().replace("\\", "/") + "/");

        // Save the config file to the new config directory
        final File configFile = new File(configDir, "ab3d.conf");
        FileUtils.writeStringToFile(configFile, config);

        // Save command status
        FileUtils.copyInputStreamToFile(Simulation.class.getResourceAsStream("/sim/cda/ab3d/config/command_status.txt"), new File(configDir, "command_status.txt"));
        // Save game id
        FileUtils.copyInputStreamToFile(Simulation.class.getResourceAsStream("/sim/cda/ab3d/config/game_id.txt"), new File(configDir, "game_id.txt"));
        // Save user accounts
        FileUtils.copyInputStreamToFile(Simulation.class.getResourceAsStream("/sim/cda/ab3d/config/user_accounts.xml"), new File(configDir, "user_accounts.xml"));

        // Create logs directory
        File logsDir = new File(simDir, "logs");
        if (logsDir.exists()) {
            FileUtils.deleteDirectory(logsDir);
        }
        if (!logsDir.exists() && !logsDir.mkdir()) {
            throw new RuntimeException("Could not create directory: " + logsDir.getAbsolutePath());
        }

        // Create past games directory
        File pastGamesDir = new File(simDir, "past_games");
        if (pastGamesDir.exists()) {
            FileUtils.deleteDirectory(pastGamesDir);
        }
        if (!pastGamesDir.exists() && !pastGamesDir.mkdir()) {
            throw new RuntimeException("Could not create directory: " + pastGamesDir.getAbsolutePath());
        }

        // Create game data directory
        File gameDataDir = new File(simDir, "game_data");
        if (gameDataDir.exists()) {
            FileUtils.deleteDirectory(gameDataDir);
        }
        if (!gameDataDir.exists() && !gameDataDir.mkdir()) {
            throw new RuntimeException("Could not create directory: " + gameDataDir.getAbsolutePath());
        }

        // Create bin directory
        File binDir = new File(simDir, "bin");
        if (!binDir.exists() && !binDir.mkdir()) {
            throw new RuntimeException("Could not create directory: " + binDir.getAbsolutePath());
        }

        printLog("Starting up the AB3D system manager");

        // Start up the AB3D System Manager
        SystemManager.main(new String[]{configFile.getAbsolutePath()});

        printLog("Waiting for the AB3D system manager to start");

        // Wait for the System Manager to start up
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
            if (e.getMessage() != null) {
                printLog("There was a problem starting the system manager: " + e.getMessage());
            }
        }

        printLog("Starting up the AB3D auction supervisor");

        // Start up the AB3D Auction Supervisor
        Thread auctionSupervisorThread = new Thread() {

            @Override
            public void run() {
                try {
                    AuctionSupervisor.main(new String[]{configFile.getAbsolutePath()});
                } catch (Exception e) {
                    if (e.getMessage() != null) {
                        printLog("There was a problem starting the auction supervisor: " + e.getMessage());
                    } else {
                        printLog("There was a problem starting the auction supervisor: null");
                    }
                }
            }
        };
        auctionSupervisorThread.start();

        printLog("Waiting for the AB3D auction supervisor to start");

        // Wait for the Auction Supervisor to start up
        try {
            Thread.sleep(10000);
        } catch (Exception e) {
            if (e.getMessage() != null) {
                printLog("Interrupted while starting the auction supervisor: " + e.getMessage());
            } else {
                printLog("Interrupted while starting the auction supervisor: null");
            }
        }

        try {
            printLog("Creating the game directory files.");
            // Make a game dir
            File gameDir = new File(gameDataDir, "game");
            if (!gameDir.exists() && !gameDir.mkdir()) {
                throw new RuntimeException("Could not create directory: " + gameDir.getAbsolutePath());
            }
            String gameXml = IOUtils.toString(Simulation.class.getResourceAsStream("/sim/cda/ab3d/game/game.xml"));
            String gameLen = String.valueOf(CDAGameConstants.AUCTION_REPETITIONS * CDAGameConstants.AUCTION_LEN);
            String players = String.valueOf(CDAGameConstants.PLAYERS);
            File gameXMLFile = new File(gameDir, "game.xml");
            gameXMLFile.createNewFile();
            FileUtils.writeStringToFile(gameXMLFile, gameXml.replace("[GAMELEN]", gameLen).replace("[PLAYERS]", players));
            File gameXSLFile = new File(gameDir, "game.xsl");
            gameXSLFile.createNewFile();
            FileUtils.copyInputStreamToFile(Simulation.class.getResourceAsStream("/sim/cda/ab3d/game/game.xsl"), gameXSLFile);
            File aucIDTemplateFile = new File(gameDir, "aucid_template.xml");
            aucIDTemplateFile.createNewFile();
            FileUtils.copyInputStreamToFile(Simulation.class.getResourceAsStream("/sim/cda/ab3d/game/aucid_template.xml"), aucIDTemplateFile);
            String auctionTemplateXML = IOUtils.toString(Simulation.class.getResourceAsStream("/sim/cda/ab3d/game/auction_template.xml"));
            StringBuilder auctionTemplate = new StringBuilder();
            for (int r = 0; r < CDAGameConstants.AUCTION_REPETITIONS; r++) {
                auctionTemplate.append("\t<trigger>\n");
                auctionTemplate.append("\t\t<when>time = gameStartTime + ");
                auctionTemplate.append((r + 1) * CDAGameConstants.AUCTION_LEN);
                auctionTemplate.append("</when>\n");
                if (r == CDAGameConstants.AUCTION_REPETITIONS - 1) {
                    auctionTemplate.append("\t\t<action>close</action>\n");
                } else {
                    auctionTemplate.append("\t\t<action>flushBids</action>\n");
                    auctionTemplate.append("\t\t<action>quote</action>\n");
                }
                auctionTemplate.append("\t</trigger>\n");
            }
            FileUtils.writeStringToFile(new File(gameDir, "auction_template.xml"), auctionTemplateXML.replace("[TRIGGERS]", auctionTemplate.toString()));
            File auctionsXMLFile = new File(gameDir, "auctions.xml");
            auctionsXMLFile.createNewFile();
            FileUtils.copyInputStreamToFile(Simulation.class.getResourceAsStream("/sim/cda/ab3d/game/auctions.xml"), auctionsXMLFile);
            File prefTemplateXMLFile = new File(gameDir, "pref_template.xml");
            prefTemplateXMLFile.createNewFile();
            FileUtils.copyInputStreamToFile(Simulation.class.getResourceAsStream("/sim/cda/ab3d/game/pref_template.xml"), prefTemplateXMLFile);
            String systemAgents = IOUtils.toString(Simulation.class.getResourceAsStream("/sim/cda/ab3d/game/system_agents.xml"));
            StringBuilder agents = new StringBuilder();
            Map<String, Collection<Object>> profile = new HashMap<String, Collection<Object>>();
            profile.put("Sellers", new LinkedList<Object>());
            profile.put("Buyers", new LinkedList<Object>());
            if (SIM_PROFILE.containsKey("ALL")) {
                Main.printLog("Seller agents:");
                for (Object o : SIM_PROFILE.get("ALL")) {
                    String strategy = ((String) o).toLowerCase();
                    for (int i = 0; i < 2; i++) {
                        String agent = getNextAgentForStrategy(strategy);
                        Main.printLog(" " + agent);
                        profile.get("Sellers").add(agent);
                        agents.append("\n\t<agentTuple><id>");
                        agents.append(getAgent(agent));
                        agents.append("</id><impType>class</impType><impName>sim.cda.");
                        agents.append(strategy.toUpperCase());
                        agents.append("</impName><userName>");
                        agents.append(agent);
                        agents.append("</userName><password>");
                        agents.append(agent);
                        agents.append("</password></agentTuple>");
                    }
                }
                Main.printLog("Buyer agents:");
                for (Object o : SIM_PROFILE.get("ALL")) {
                    String strategy = ((String) o).toLowerCase();
                    for (int i = 0; i < 2; i++) {
                        String agent = getNextAgentForStrategy(strategy);
                        Main.printLog(" " + agent);
                        BUYERS.add(agent);
                        profile.get("Buyers").add(agent);
                        agents.append("\n\t<agentTuple><id>");
                        agents.append(getAgent(agent));
                        agents.append("</id><impType>class</impType><impName>sim.cda.");
                        agents.append(strategy.toUpperCase());
                        agents.append("</impName><userName>");
                        agents.append(agent);
                        agents.append("</userName><password>");
                        agents.append(agent);
                        agents.append("</password></agentTuple>");
                    }
                }
            }
            File systemAgentsXMLFile = new File(gameDir, "system_agents.xml");
            systemAgentsXMLFile.createNewFile();
            FileUtils.writeStringToFile(systemAgentsXMLFile, systemAgents.replace("[AGENTS]", agents.toString()));

            printLog("Done writing files. Waiting for them to show up.");

            // Wait for files to show up
            Thread.sleep(10000);

            printLog("Done waiting for files to show up.");
            printLog("Game XML file exists? " + gameXMLFile.exists());
            printLog("Game XSL file exists? " + gameXSLFile.exists());
            printLog("Auctions ID template file exists? " + aucIDTemplateFile.exists());
            printLog("Auctions XML file exists? " + auctionsXMLFile.exists());
            printLog("Pref template XML file exists? " + prefTemplateXMLFile.exists());
            printLog("System agents XML file exists? " + systemAgentsXMLFile.exists());

            // Open stream to payoffs file
            FileOutputStream payoffOut = new FileOutputStream(payoffFile);
            Map<String, FileOutputStream> featuresOut = new HashMap<String, FileOutputStream>();
            try {
                // Collect samples
                for (int i = 1; i <= samples; i++) {
                    try {
                        printLog("");
                        printLog("Sample: " + i);
                        printLog("Collecting sample #" + i);

                        // Run sim
                        Simulation sim = new Simulation(i, pastGamesDir, profile, simParams);
                        SimulationResults results = sim.run();

                        printLog("Finished running sample #" + i);

                        // Print results
                        for (String role : results.payoffs.keySet()) {
                            Main.printLog(role + ":");
                            for (String strategy : results.payoffs.get(role).keySet()) {
                                Main.printLog(" " + strategy + ": " + results.payoffs.get(role).get(strategy));
                            }
                        }

                        // Combine BUYERS and SELLERS roles to ALL
                        Map<String, Object> allPayoffs = new HashMap<String, Object>();
                        // Merge in the payoffs to ALL
                        for (Object o : SIM_PROFILE.get("ALL")) {
                            String strategy = (String) o;
                            double score = (((Double) results.payoffs.get("Sellers").get(strategy)).doubleValue() + ((Double) results.payoffs.get("Buyers").get(strategy)).doubleValue()) / 2d;
                            allPayoffs.put(strategy, (Object) score);
                        }
                        // Switch out the ALL role for the buyers and sellers roles
                        results.payoffs.remove("Buyers");
                        results.payoffs.remove("Sellers");
                        results.payoffs.put("ALL", allPayoffs);

                        // Save payoffs
                        payoffOut.write(("---" + Utils.LINE_BREAK).getBytes());
                        payoffOut.write(("ALL:" + Utils.LINE_BREAK).getBytes());
                        for (String strategy : allPayoffs.keySet()) {
                            payoffOut.write(("  " + strategy + ": " + String.valueOf(((Double) allPayoffs.get(strategy)).doubleValue()) + Utils.LINE_BREAK).getBytes());
                        }

//                        // Save features
//                        for (String feature : results.features.keySet()) {
//                            // New feature
//                            if (!featuresOut.containsKey(feature)) {
//                                // Create file
//                                File featureFile = new File(featuresDir, feature);
//                                if (!featureFile.exists() && !featureFile.createNewFile()) {
//                                    throw new RuntimeException("Could not create feature file: " + featureFile.getAbsolutePath());
//                                }
//                                // Open stream to file
//                                featuresOut.put(feature, new FileOutputStream(featureFile));
//                            }
//                            // Save feature data
//                            featuresOut.get(feature).write(Yaml.dump(results.features.get(feature)).getBytes());
//                        }
                    } catch (Exception e) {
                        if (e != null) {
                            printLog("Error collecting sample: " + e.getMessage());
                        } else {
                            printLog("Error collecting sample: null");
                        }
                    } finally {
                        // Clean up the AB3D logging that shouldn't be happening
                        try {
                            File pastGameDir = new File(pastGamesDir, String.valueOf(i));
                            if (pastGameDir.exists()) {
                                // AB3D Log
                                File ab3dLogFile = new File(pastGameDir, "ab3d.log");
                                if (ab3dLogFile.exists()) {
                                    // Empty the file
                                    try {
                                        FileUtils.write(ab3dLogFile, "");
                                    } catch (Exception e) {
                                    }
                                    // Try to delete it
                                    FileUtils.deleteQuietly(ab3dLogFile);
                                }

                                // Game transcript
                                File scGameDataFile = new File(pastGameDir, "sc_game_data.log");
                                if (scGameDataFile.exists()) {
                                    // Empty the file
                                    try {
                                        FileUtils.write(scGameDataFile, "");
                                    } catch (Exception e) {
                                    }
                                    // Try to delete it
                                    FileUtils.deleteQuietly(scGameDataFile);
                                }

                                // HTML output
                                File htmlFile = new File(pastGameDir, "1.html");
                                if (htmlFile.exists()) {
                                    // Empty the file
                                    try {
                                        FileUtils.write(htmlFile, "");
                                    } catch (Exception e) {
                                    }
                                    // Try to delete it
                                    FileUtils.deleteQuietly(htmlFile);
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            } finally {
                Utils.safeClose(payoffOut);
                for (String feature : featuresOut.keySet()) {
                    Utils.safeClose(featuresOut.get(feature));
                }
            }
        } finally {
            // Shut down ab3d
            Utils.send("<shutdown/>", GAME_SCHEDULER);
            Utils.send("<shutdown/>", AGENT_MANAGER);
            // Shut down the logger
            if (LOG_PRINTSTREAM != null) {
                try {
                    LOG_PRINTSTREAM.flush();
                } catch (Exception e) {
                }
                try {
                    LOG_PRINTSTREAM.close();
                } catch (Exception e) {
                }
            }
        }
    }
}
