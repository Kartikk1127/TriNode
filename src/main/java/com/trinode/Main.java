package com.trinode;

import com.trinode.config.NodeConfig;

public class Main {
    public static void main(String[] args) {
        NodeConfig config = new NodeConfig();
        System.out.println("Node count: " + config.getNodeCount());
        System.out.println("Node 1 address: " + config.getNodeAddress(1));
        System.out.println("All nodes : " + config.getAllNodes());
    }
}