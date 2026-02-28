package com.trinode;

import com.trinode.config.NodeConfig;
import com.trinode.core.Node;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("===Trinode testing ===\n");

        // load configuration
        NodeConfig config = new NodeConfig();
        System.out.println("Loaded " + config.getNodeCount() + " nodes from config file. ");

        // create nodes
        Node node1 = new Node(1, config);
        Node node2 = new Node(2, config);
        Node node3 = new Node(3, config);

        // start all nodes
        System.out.println("Starting nodes...");
        node1.start();
        node2.start();
        node3.start();

        // wait for connections to establish
        System.out.println("\nWaiting 3 seconds for connections to establish...\n");
        Thread.sleep(3000);

        // simulate node2 crash
        System.out.println("\n === Simulating Node 2 crash ===\n");
        node2.stop();

        // observe node1 and node3 detecting node2 as dead
        System.out.println("Waiting for Node 1 and Node 3 to detect Node 2 as DEAD...");
        Thread.sleep(10000);

        // stop remaining nodes
        node1.stop();
        node3.stop();

        System.out.println("Test completed");
    }
}