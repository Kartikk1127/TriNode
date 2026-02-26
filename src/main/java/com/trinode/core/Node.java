package com.trinode.core;

import com.trinode.config.NodeConfig;
import com.trinode.network.Message;
import com.trinode.network.NetworkManager;
import com.trinode.storage.DataStore;

import java.util.concurrent.atomic.AtomicBoolean;

public class Node {
    private final int nodeId;
    private volatile Role role;
    private volatile int generation;
    private final DataStore data;
    private final AtomicBoolean isRunning;
    private final NetworkManager networkManager;
    private final NodeConfig config;

    public Node(int nodeId, NodeConfig config) {
        this.nodeId = nodeId;
        this.role = Role.FOLLOWER; // start as a follower
        this.generation = 0;
        this.data = new DataStore();
        this.isRunning = new AtomicBoolean(false);
        this.config = config;

        // extract port from config address
        String address = config.getNodeAddress(nodeId);
        String[] parts = address.split(":");
        int port = Integer.parseInt(parts[1]);

        this.networkManager = new NetworkManager(nodeId, port, config);
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    // start
    public void start() {
        if (isRunning.get()) {
            System.out.println("Node " + nodeId + " is already running");
            return;
        }

        System.out.println("Node " + nodeId + " starting...");
        isRunning.set(true);

        // set up message handler for network manager
        networkManager.setMessageHandler((this::handleMessage));

        networkManager.start();

        // start heartbeat threads
        // trigger leader election

        System.out.println("Node " + nodeId + " started as " + role);
    }

    // handle incoming messages from other nodes
    private void handleMessage(int fromNodeId, Message message) {
        System.out.println("Node " + nodeId + " received " + message.getType() + " from Node " + fromNodeId);

        //implement message handling
        switch (message.getType()) {
            case HEARTBEAT :
                break;
            case HEARTBEAT_ACK :
                break;
            case ELECTION_REQUEST :
                break;
            case ELECTION_RESPONSE :
                break;
            case REPLICATE_DATA :
                break;
            case REPLICATE_ACK :
                break;
            default :
                System.err.println("Unknown message type: " + message.getType());
        }
    }

    // stop
    public void stop() {
        if (!isRunning.get()) {
            System.out.println("Node " + nodeId + " already stopped");
            return;
        }

        System.out.println("Node " + nodeId + " stopping now...");

        // stop heartbeat threads
        // close network conditions

        networkManager.stop();

        data.clear();
        isRunning.set(false);

        System.out.println("Node " + nodeId + " stopped");
    }

    // become leader
    public void becomeLeader() {
        System.out.println("Node " + nodeId + " becoming LEADER");
        this.role = Role.LEADER;
        this.generation++; // increment generation when becoming leader

        // announce leadership
        // start accepting write requests
    }

    // become follower
    public void becomeFollower() {
        System.out.println("Node " + nodeId + " becoming FOLLOWER");
        this.role = Role.FOLLOWER;

        // don't clear data yet - will sync from leader
        // start listening for replication from leader
    }

    public int getNodeId() {
        return nodeId;
    }

    public Role getRole() {
        return role;
    }

    public int getGeneration() {
        return generation;
    }

    public DataStore getData() {
        return data;
    }

    public AtomicBoolean isRunning() {
        return isRunning;
    }
}
