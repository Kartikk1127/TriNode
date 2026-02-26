package com.trinode.network;

import com.trinode.config.NodeConfig;

import java.io.*;
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

        // get socket for the target node
        Socket socket = connections.get(targetNodeId);
        if (socket == null) {
            System.out.println("No connection to node " + targetNodeId);
            return;
        }
        // send in a separate thread
        senderExecutor.submit(() -> {
            try {
                // serialize the message
                byte[] data = MessageSerializer.serialize(message);
                // get output stream
                OutputStream out = socket.getOutputStream();

                // write length first (4 bytes) so receiver knows how much to read
                DataOutputStream dos = new DataOutputStream(out);
                dos.writeInt(data.length);
                // write the actual message bytes
                dos.write(data);
                dos.flush();

                System.out.println("Node " + nodeId + " sent " + message.getType() + " " + message.getPayload() + " to Node " + targetNodeId + " (" + data.length + " bytes)");
            } catch (IOException e) {
                System.err.println("Failed to send message to node " + targetNodeId + ": " + e.getMessage());
                // connection broken
                connections.remove(targetNodeId);
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        });

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
            String[] parts = address.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            System.out.println("Node " + nodeId + " connecting to Node " + targetNodeId + " at " + address);

            Socket socket = new Socket(host, port);

            // Send our node ID first so remote node knows who we are
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(nodeId);
            out.flush();

            // Atomically store connection - only if not already present
            Socket existing = connections.putIfAbsent(targetNodeId, socket);

            if (existing != null) {
                // Someone else already connected, close our socket
                System.out.println("Node " + nodeId + " already has connection to Node " + targetNodeId +
                        ", closing duplicate outgoing");
                socket.close();
                return;
            }

            System.out.println("Node " + nodeId + " connected to node " + targetNodeId);

            // Start reading messages from this node
            listenerExecutor.submit(() -> readMessageFromSocket(targetNodeId, socket));

        } catch (IOException e) {
            System.err.println("Node " + nodeId + " failed to connect to node " + targetNodeId + ": " +
                    e.getMessage());
        }
    }

    private void handleIncomingConnection(Socket socket) {
        try {
            // Read the remote node's ID first
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int remoteNodeId = in.readInt();

            System.out.println("Node " + nodeId + " identified incoming connection from node " + remoteNodeId);

            // Atomically store connection - only if not already present
            Socket existing = connections.putIfAbsent(remoteNodeId, socket);

            if (existing != null) {
                // Already have a connection, close this incoming one
                System.out.println("Node " + nodeId + " already connected to Node " + remoteNodeId +
                        ", closing duplicate incoming");
                socket.close();
                return;
            }

            // We stored the connection, start reading
            listenerExecutor.submit(() -> readMessageFromSocket(remoteNodeId, socket));

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
        System.out.println("Node " + nodeId + " started reading messages from Node " + remoteNodeId);

        try (socket) {
            DataInputStream dis = new DataInputStream(socket.getInputStream());

            while (running.get() && !socket.isClosed()) {
                try {
                    // read message length first
                    int length = dis.readInt();

                    if (length <= 0 || length > 10_000_000) { // sanity check (10 MB max)
                        System.err.println("Invalid message length: " + length);
                    }

                    // read the exact number of bytes
                    byte[] data = new byte[length];
                    dis.readFully(data); // blocks until all bytes read

                    // deserialize to message
                    Message message = MessageSerializer.deserialize(data);

                    System.out.println("Node " + nodeId + " received " + message.getType() + " from Node " + remoteNodeId + " (" + length + " bytes)");

                    // call message handler if set
                    if (messageHandler != null) {
                        messageHandler.onMessage(remoteNodeId, message);
                    } else {
                        System.err.println("No message handler set, dropping message");
                    }
                } catch (EOFException e) {
                    // connection closed by remote node
                    System.err.println("Node " + remoteNodeId + " disconnected");
                    break;
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("Error reading from node " + remoteNodeId + ": " + e.getMessage());
                    }
                    break;
                } catch (ClassNotFoundException e) {
                    System.err.println("Failed to deserialize message: " + e.getMessage());
                    // continue reading, don't break on one bad message
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to get input stream from node " + remoteNodeId);
        } finally {
            // clean up connections
            connections.remove(remoteNodeId);
            //ignore
            // ignored
            System.out.println("Node " + nodeId + " stopped reading from Node " + remoteNodeId);
        }
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
