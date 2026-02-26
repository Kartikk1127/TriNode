package com.trinode;

import com.trinode.config.NodeConfig;
import com.trinode.core.Node;
import com.trinode.network.Message;
import com.trinode.network.MessageType;

import static com.trinode.network.MessageSerializer.deserialize;
import static com.trinode.network.MessageSerializer.serialize;

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

        // test 1: send message from node1 to node 2
        System.out.println("Test 1: Node 1 -> Node 2");
        Message message1 = new Message(MessageType.HEARTBEAT, 1, 0, "Hello from Node 1");
        node1.getNetworkManager().sendMessage(2, message1);
        Thread.sleep(1000);

        // test 2: send message from node2 to node3
        System.out.println("Test 1: Node 2 -> Node 3");
        Message message2 = new Message(MessageType.HEARTBEAT, 2, 0, "Hello from Node 2");
        node1.getNetworkManager().sendMessage(3, message2);
        Thread.sleep(1000);

        // broadcast from node3 to all
        System.out.println("Test 1: Node 3 -> BROADCAST");
        Message message3 = new Message(MessageType.HEARTBEAT, 3, 0, "Broadcast from Node 3");
        node1.getNetworkManager().broadcastMessage(message3);
        Thread.sleep(1000);

        // datastore operations
        System.out.println("Test 4: Datastore operations");
        node1.getData().put("kartikey", "started the project");
        String value = node1.getData().get("kartikey");
        System.out.println("Node 1 Datastore - Get 'kartikey': " + value);
        System.out.println("Node 1 Datastore size: " + node1.getData().size());

        node1.getData().delete("kartikey");
        System.out.println("After delete size: " + node1.getData().size());

        System.out.println("\n Letting messages process...");
        Thread.sleep(2000);

        // stop all nodes
        node1.stop();
        node2.stop();
        node3.stop();

        System.out.println("Test completed");
    }
}