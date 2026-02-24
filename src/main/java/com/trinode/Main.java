package com.trinode;

import com.trinode.config.NodeConfig;
import com.trinode.core.Node;
import com.trinode.network.Message;
import com.trinode.network.MessageType;

import static com.trinode.network.MessageSerializer.deserialize;
import static com.trinode.network.MessageSerializer.serialize;

public class Main {
    public static void main(String[] args) {

        NodeConfig config = new NodeConfig();
        Node node1 = new Node(1, config);
        Node node2 = new Node(2, config);
        Node node3 = new Node(3, config);

        node1.start();
        node2.start();
        node3.start();
    }
}