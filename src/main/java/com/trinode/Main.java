package com.trinode;

import com.trinode.config.NodeConfig;
import com.trinode.core.Node;
import com.trinode.storage.DataStore;

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        NodeConfig config = new NodeConfig();
        Map<Integer, String > allNodes = config.getAllNodes();
        Node node1 = new Node(1);
        Node node2 = new Node(2);
        Node node3 = new Node(3);

        node1.start();
        node2.start();
        node3.start();

        DataStore ds = node1.getData();
        ds.put("kartikey","started the project");
        System.out.println(ds.size());
        String value = ds.get("kartikey");
        System.out.println(value);
        ds.delete("kartikey");
        System.out.println(ds.size());

        node1.stop();
        node2.stop();
        node3.stop();
    }
}