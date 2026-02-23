package com.trinode.core;

import com.trinode.storage.DataStore;

import java.util.concurrent.atomic.AtomicBoolean;

public class Node {
    private final int nodeId;
    private volatile Role role;
    private volatile int generation;
    private final DataStore data;
    private final AtomicBoolean isRunning;

    public Node(int nodeId) {
        this.nodeId = nodeId;
        this.role = Role.FOLLOWER; // start as a follower
        this.generation = 0;
        this.data = new DataStore();
        this.isRunning = new AtomicBoolean(false);
    }

    // start
    public boolean start() {
        if (isRunning.get()) {
            System.out.println("Node " + nodeId + " is already running");
            return false;
        }

        System.out.println("Node " + nodeId + " starting...");
        isRunning.set(true);

        // initialize network manager
        // connect to other nodes
        // start heartbeat sender/receiver
        // trigger leader election

        System.out.println("Node " + nodeId + " started as " + role);
        return true;
    }

    // stop
    public boolean stop() {
        if (!isRunning.get()) {
            System.out.println("Node " + nodeId + " already stopped");
            return false;
        }

        System.out.println("Node " + nodeId + " stopping now...");

        // stop heartbeat threads
        // close network conditions

        data.clear();
        isRunning.set(false);

        System.out.println("Node " + nodeId + " stopped");
        return true;
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
