package com.trinode.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class NodeConfig {
    private final Map<Integer, String> nodeAddresses;
    private static final String CONFIG_FILE = "node.conf";

    public NodeConfig() {
        this.nodeAddresses = new HashMap<>();
        loadConfig();
    }

    private void loadConfig() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new RuntimeException("Config file not found: " + CONFIG_FILE);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            while((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) continue;

                parseLine(line);
            }
            System.out.println("Loaded " + nodeAddresses.size() + " nodes from config file");

        } catch (IOException e) {
            throw new RuntimeException("Failed to load config file", e);
        }
    }

    private void parseLine(String line) {
        String[] parts = line.split("=");

        if (parts.length != 2) {
            System.out.println("Invalid config line: " + line);
            return;
        }

        String nodeIdStr = parts[0].trim().replace("node", "");
        String address = parts[1].trim();

        try {
            int nodeId = Integer.parseInt(nodeIdStr);
            nodeAddresses.put(nodeId, address);
        } catch (NumberFormatException e) {
            System.err.println("Invalid node ID in line: " + line);
        }
    }

    public String getNodeAddress(int nodeId) {
        return nodeAddresses.get(nodeId);
    }

    public Map<Integer, String > getAllNodes() {
        return new HashMap<>(nodeAddresses);
    }

    public int getNodeCount() {
        return nodeAddresses.size();
    }

    public boolean hasNode(int nodeId) {
        return nodeAddresses.containsKey(nodeId);
    }


}
