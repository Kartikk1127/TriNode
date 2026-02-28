package com.trinode.network;

import com.trinode.config.NodeConfig;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;
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
    private final Map<Integer, Long> lastHeartbeatTime;
    private final Map<Integer, NodeStatus> peerStatus;
    private final Map<Integer, ScheduledFuture<?>> watchdogTimers;
    private ScheduledExecutorService heartbeatScheduler;
    private NodeFailureHandler failureHandler;


    public NetworkManager(int nodeId, int port, NodeConfig config) {
        this.nodeId = nodeId;
        this.port = port;
        this.config = config;
        this.connections = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.lastHeartbeatTime = new ConcurrentHashMap<>();
        this.peerStatus = new ConcurrentHashMap<>();
        this.watchdogTimers = new ConcurrentHashMap<>();
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
        listenerExecutor = Executors.newCachedThreadPool();
        senderExecutor = Executors.newCachedThreadPool();
        startHeartbeatSender();

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
        if (heartbeatScheduler!=null) {
            heartbeatScheduler.shutdownNow();
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

    public void setFailureHandler(NodeFailureHandler handler) {
        this.failureHandler = handler;
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

            if (nodeId < targetNodeId) {
                // we are lower - our outgoing is canonical, store it.
                Socket old = connections.put(targetNodeId, socket);
                if (old!=null) {
                    // let its reader die naturally
                    try {
                        old.close();
                    } catch (IOException ignored){}
                }
                System.out.println("Node " + nodeId + " stored canonical outgoing to Node " + targetNodeId);
                startWatchDog(targetNodeId);
                listenerExecutor.submit(() -> readMessageFromSocket(targetNodeId, socket));
            } else {
                // we are higher - our outgoing is non-canonical, close it.
                System.out.println("Node " +  nodeId + " dropping non-canonical outgoing to Node " + targetNodeId);
                sendConnectionClose(socket);
                socket.close();
            }
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

            if (remoteNodeId < nodeId) {
                // incoming from lower node - store it
                Socket old = connections.put(remoteNodeId, socket);
                if (old != null) {
                    try {
                        old.close();
                    } catch (IOException ignored) {}
                }
                System.out.println("Node " + nodeId + " stored canonical incoming from Node " + remoteNodeId);
                startWatchDog(remoteNodeId);
                listenerExecutor.submit(() -> readMessageFromSocket(remoteNodeId, socket));
            } else {
                // incoming from higher node - non-canonical group
                System.out.println("Node " + nodeId + " dropping non-canonical incoming from Node: " + remoteNodeId);
                sendConnectionClose(socket);
                socket.close();
            }

        } catch (IOException e) {
            System.err.println("Error handling incoming connection: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private void sendConnectionClose(Socket socket) {
        try {
            Message closeMsg = new Message(MessageType.CONNECTION_CLOSE, nodeId, 0, "");
            byte[] data = MessageSerializer.serialize(closeMsg);
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            dos.writeInt(data.length);
            dos.write(data);
            dos.flush();
        } catch (IOException ignored) {}
    }

    private void readMessageFromSocket(int remoteNodeId, Socket socket) {
        System.out.println("Node " + nodeId + " started reading messages from Node " + remoteNodeId);
        boolean intentionalClose = false;

        try (socket) {
            DataInputStream dis = new DataInputStream(socket.getInputStream());

            while (running.get() && !socket.isClosed()) {
                try {
                    // read message length first
                    int length = dis.readInt();

                    if (length <= 0 || length > 10_000_000) { // sanity check (10 MB max)
                        System.err.println("Invalid message length: " + length);
                        continue;
                    }

                    // read the exact number of bytes
                    byte[] data = new byte[length];
                    dis.readFully(data); // blocks until all bytes read

                    // deserialize to message
                    Message message = MessageSerializer.deserialize(data);

                    System.out.println("Node " + nodeId + " received " + message.getType() + " " + message.getPayload() + " " + " from Node " + remoteNodeId + " (" + length + " bytes)");

                    if (message.getType()==MessageType.CONNECTION_CLOSE) {
                        System.out.println("Node " + nodeId + " received CONNECTION_CLOSE from Node: " + remoteNodeId + ", closing cleanly");
                        intentionalClose = true;
                        break;
                    }
                    if (message.getType() == MessageType.HEARTBEAT) {
                        lastHeartbeatTime.put(remoteNodeId, System.currentTimeMillis());
                        if (peerStatus.get(remoteNodeId)==NodeStatus.DEAD) {
                            System.out.println("Node " + nodeId + " detected node " + remoteNodeId + " recovered.");
                            peerStatus.put(remoteNodeId, NodeStatus.ALIVE);
                            if (failureHandler != null) {
                                failureHandler.onNodeRecovered(remoteNodeId);
                            }
                        }
                    }
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
            stopWatchdog(remoteNodeId);
            System.out.println("Node " + nodeId + " stopped reading from Node " + remoteNodeId);
            if (running.get() && !intentionalClose) {
                System.out.println("Node " + nodeId + " attempting reconnect to Node " + remoteNodeId);
                reconnect(remoteNodeId);
            }
        }
    }

    private void reconnect(int targetNodeId) {
        String address = config.getNodeAddress(targetNodeId);
        if (address!=null) {
            senderExecutor.submit(() -> connectToNode(targetNodeId, address));
        } else {
            System.err.println("Node " + nodeId + " has no address for Node " + targetNodeId + ", cannot reconnect");
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

    private void startHeartbeatSender() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            Message heartbeat = new Message(MessageType.HEARTBEAT, nodeId, 0, "");
            broadcastMessage(heartbeat);
        },0,1,TimeUnit.SECONDS);
    }

    private void startWatchDog(int peerId) {
        // initialize last heartbeat time to now so we don't immediately mark as dead
        lastHeartbeatTime.put(peerId, System.currentTimeMillis());
        peerStatus.put(peerId, NodeStatus.ALIVE);

        ScheduledFuture<?> watchDog = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            Long last = lastHeartbeatTime.get(peerId);
            if (last == null) return;

            long elapsed = System.currentTimeMillis() - last;
            if (elapsed > 3000 && peerStatus.get(peerId) == NodeStatus.ALIVE) {
                System.out.println("Node " + nodeId + " detected node " + peerId + " as Dead.");
                peerStatus.put(peerId, NodeStatus.DEAD);
                connections.remove(peerId);
                if (failureHandler != null) {
                    failureHandler.onNodeDead(peerId);
                }
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
        watchdogTimers.put(peerId, watchDog);
    }

    private void stopWatchdog(int peerId) {
        ScheduledFuture<?> watchdog = watchdogTimers.remove(peerId);
        if (watchdog != null) {
            watchdog.cancel(false);
        }
        lastHeartbeatTime.remove(peerId);
        peerStatus.remove(peerId);
    }

    // callback interface for handling received messages
    public interface MessageHandler {
        void onMessage(int fromNodeId, Message message);
    }

    public interface NodeFailureHandler {
        void onNodeDead(int nodeId);
        void onNodeRecovered(int nodeId);
    }
}
