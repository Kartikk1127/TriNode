package com.trinode.network;

import com.trinode.config.NodeConfig;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkManager {
    private final int nodeId;
    private final int port;
    private final NodeConfig config;

    // socket connections to other nodes
    private final Map<Integer, Socket> connections;

    // server socket for accepting incoming connections
    private ServerSocket serverSocket;

    // thread pools for handling connections
    private ExecutorService listenerExecutor;
    private ExecutorService senderExecutor;

    // running state
    private final AtomicBoolean running;

    // message handler (callback when message received)
    private MessageHandler messageHandler;

    public NetworkManager(int nodeId, int port, NodeConfig config) {
        this.nodeId = nodeId;
        this.port = port;
        this.config = config;
        this.connections = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
    }

    // start the network manager
    // start server socket listener
    // connect to all nodes
    public void start() {
        if (running.get()) {
            System.out.println("Network Manager already running");
            return;
        }

        System.out.println("Network Manager starting on port: " + port);
        running.set(true);

        // initialize thread pools
        listenerExecutor = Executors.newFixedThreadPool(3);
        senderExecutor = Executors.newFixedThreadPool(3);

        // start socket listener
        startServerListener();

        // connect to all other nodes
        connectToAllNodes();

        System.out.println("Network manager started");
    }

    // stop the network manager
    // close all connections
    // shutdown thread pools
    public void stop() {
        if (!running.get()) {
            return;
        }

        System.out.println("NetworkManager stopping...");
        running.set(false);

        // close all socket connections
        closeAllConnections();

        // close server socket
        closeServerSocket();

        // shutdown thread pools
        if (listenerExecutor!=null) {
            listenerExecutor.shutdownNow();
        }
        if (senderExecutor!=null) {
            senderExecutor.shutdownNow();
        }

        System.out.println("NetworkManager stopped");
    }

    // send message to a specific node
    public void sendMessage(int targetNodeId, Message message) {
        if (!running.get()) {
            System.out.println("NetworkManager not running");
            return;
        }

        // implement message sending
        // get socket for targetNodeId
        // serialize message using message serializer
        // send bytes over socket
        // handle errors

        System.out.println("Message sent to node: " + targetNodeId);
    }

    // broadcast message to all nodes
    public void broadcastMessage(Message message) {
        for (Integer nodeId : connections.keySet()) {
            sendMessage(nodeId, message);
        }
    }

    // set the message handler (callback for received message)
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    private void startServerListener() {
        listenerExecutor.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Node " + nodeId + " listening on port " + port);

                while (running.get()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("Node " + nodeId + " accepted connection from " + clientSocket.getRemoteSocketAddress());

                        // handle this connection in a separate thread
                        listenerExecutor.submit(() -> handleIncomingConnection(clientSocket));
                    } catch (IOException e) {
                        if (running.get()) {
                            System.err.println("Error accepting connection: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to start server socket on port " + port + ": " + e.getMessage());
            }
        });
    }

    private void connectToAllNodes() {
        Map<Integer, String> allNodes = config.getAllNodes();

        for (Map.Entry<Integer, String> entry : allNodes.entrySet()) {
            int targetNodeId = entry.getKey();
            String address = entry.getValue();

            // don't connect to self
            if (targetNodeId == nodeId) continue;

            senderExecutor.submit(() -> connectToNode(targetNodeId, address));
        }
    }

    private void connectToNode(int targetNodeId, String address) {
        try {
            String [] parts = address.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            System.out.println("Node " + nodeId + " connecting to Node " + targetNodeId + " at " + address);

            Socket socket = new Socket(host, port);
            // send our node id first so remote node knows who we are
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(nodeId);
            out.flush();

            // store connection
            connections.put(targetNodeId, socket);
            System.out.println("Node " + nodeId + " connected to node " + targetNodeId);
            // start reading messages from this node
            listenerExecutor.submit(()-> readMessageFromSocket(targetNodeId, socket));
        } catch (IOException e) {
            System.err.println("Node " + nodeId + " failed to connect to node " + targetNodeId + ": " + e.getMessage());
        }
    }

    private void handleIncomingConnection(Socket socket) {
        try {
            // read the remote node's id first
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int remoteNodeId = in.readInt();

            System.out.println("Node " + nodeId + " identified incoming connection from node " + remoteNodeId);

            // check if we already have a connection to this node
            if (connections.containsKey(remoteNodeId)) {
                System.out.println("Node " + nodeId + " already connected to Node " + remoteNodeId + ", closing duplicate");
                socket.close();
                return;
            }

            // store connection
            connections.put(remoteNodeId, socket);

            // start reading messages from this node
            readMessageFromSocket(remoteNodeId, socket);
        } catch (IOException e) {
            System.err.println("Error handling incoming connection: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private void readMessageFromSocket(int remoteNodeId, Socket socket) {
        // loop while running
        // read bytes from socket
        // deserialize
        // call messagehandler.onmessage(remoteNodeId, message)
        // handle disconnections

        System.out.println("Read messages from node " + remoteNodeId);
    }

    private void closeAllConnections() {
        for (Map.Entry<Integer, Socket> entry : connections.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                System.err.println("Error closing connection to node: " + entry.getKey());
            }
        }
        connections.clear();
    }

    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket");
            }
        }
    }

    // callback interface for handling received messages
    public interface MessageHandler {
        void onMessage(int fromNodeId, Message message);
    }
}
