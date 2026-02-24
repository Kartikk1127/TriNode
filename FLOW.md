# COMPLETE FLOW – TCP NETWORKING EXPLAINED

## SCENARIO: 3 NODES STARTING UP

This walks through exactly what happens when `Main.java` runs.

---

# PHASE 1: STARTUP (Main.java)

### Main.java – line by line

```java
NodeConfig config = new NodeConfig("/node.conf");
// Reads config file, creates map:
// {1 -> "localhost:8001", 2 -> "localhost:8002", 3 -> "localhost:8003"}

Node node1 = new Node(1, config);
Node node2 = new Node(2, config);
Node node3 = new Node(3, config);
// Creates 3 Node objects
````

---

## Inside Node constructor

```java
this.nodeId = nodeId;                 // 1, 2, or 3
this.role = Role.FOLLOWER;            // Everyone starts as follower
this.generation = 0;                  // Generation 0
this.dataStore = new DataStore();     // Empty TreeMap wrapped in ReadWriteLock
this.running = new AtomicBoolean(false);
this.config = config;

// Extract port from config
String address = config.getNodeAddress(nodeId);
// Node 1 → "localhost:8001"

String[] parts = address.split(":");
// ["localhost", "8001"]

int port = Integer.parseInt(parts[1]);
// port = 8001

this.networkManager = new NetworkManager(nodeId, port, config);
```

---

## Inside NetworkManager constructor

```java
this.nodeId = nodeId;
this.port = port;
this.config = config;
this.connections = new ConcurrentHashMap<>();
this.running = new AtomicBoolean(false);
```

### State after Phase 1

* 3 Node objects exist.
* Each has a NetworkManager.
* Nothing is running yet.
* No sockets are open.

---

# PHASE 2: node.start()

```java
node1.start();
node2.start();
node3.start();
```

---

## Inside Node.start()

```java
if (running.get()) return;

System.out.println("Node " + nodeId + " starting as " + role);
running.set(true);

// Set message callback
networkManager.setMessageHandler((fromNodeId, message) -> {
    handleMessage(fromNodeId, message);
});
```

### What this means

NetworkManager is told:

> "When you receive a message, call my handleMessage() method."

---

## Inside NetworkManager.setMessageHandler()

```java
this.messageHandler = handler;
```

It simply stores the callback.

---

Back to `Node.start()`:

```java
networkManager.start();  // Start networking
```

---

# PHASE 3: NetworkManager.start()

```java
if (running.get()) return;

System.out.println("NetworkManager starting on port " + port);
running.set(true);

listenerExecutor = Executors.newFixedThreadPool(3);
senderExecutor = Executors.newFixedThreadPool(3);

startServerListener();   // Passive mode
connectToAllNodes();     // Active mode
```

---

# METHOD 1: startServerListener() — PASSIVE MODE

Purpose: Accept connections from other nodes.

```java
listenerExecutor.submit(() -> {
    try {
        serverSocket = new ServerSocket(port);

        while (running.get()) {
            Socket clientSocket = serverSocket.accept();
            listenerExecutor.submit(() ->
                handleIncomingConnection(clientSocket));
        }
    } catch (IOException e) {
        ...
    }
});
```

### What this does

1. Opens ServerSocket on port (8001 / 8002 / 8003)
2. Blocks on `accept()`
3. When someone connects:

    * Returns a Socket
    * Spawns new thread to handle it
4. Immediately goes back to `accept()`

---

# METHOD 2: connectToAllNodes() — ACTIVE MODE

Purpose: Actively connect to other nodes.

```java
Map<Integer, String> allNodes = config.getAllNodes();

for (Map.Entry<Integer, String> entry : allNodes.entrySet()) {
    int targetNodeId = entry.getKey();
    String address = entry.getValue();

    if (targetNodeId == nodeId) continue;

    senderExecutor.submit(() ->
        connectToNode(targetNodeId, address));
}
```

### Example for Node 1

* Skip Node 1 (self)
* Connect to Node 2
* Connect to Node 3

Each connection attempt runs in its own thread.

---

# METHOD 3: connectToNode() — ACTIVE CONNECTION

```java
String[] parts = address.split(":");
String host = parts[0];
int port = Integer.parseInt(parts[1]);

Socket socket = new Socket(host, port);
```

This:

* Connects to target’s ServerSocket
* Unblocks their `accept()`

---

## Send our node ID

```java
DataOutputStream out = new DataOutputStream(socket.getOutputStream());
out.writeInt(nodeId);
out.flush();
```

4 bytes are sent containing our ID.

---

## Store connection

```java
connections.put(targetNodeId, socket);
```

Now we have:

```
connections = {2 -> socketToNode2}
```

---

## Start reading from socket

```java
listenerExecutor.submit(() ->
    readMessagesFromSocket(targetNodeId, socket));
```

---

# METHOD 4: handleIncomingConnection()

Triggered when someone connects to me.

```java
DataInputStream in = new DataInputStream(socket.getInputStream());
int remoteNodeId = in.readInt();
```

We read the 4-byte node ID they sent.

---

## Duplicate Detection

```java
if (connections.containsKey(remoteNodeId)) {
    socket.close();   // Close duplicate
    return;
}
```

If we already connected to them, close this socket.

Otherwise:

```java
connections.put(remoteNodeId, socket);
readMessagesFromSocket(remoteNodeId, socket);
```

---

# THE RACE CONDITION

Both nodes connect to each other at the same time.

Timeline:

```
t=0: Node1 connects to Node2
t=0: Node2 connects to Node1
t=1: Both send IDs
t=2: Both store outgoing connections
t=3: Both accept incoming connections
t=4: Duplicate detected
t=5: One socket closed
```

Final state:

Each pair of nodes has exactly ONE connection.

---

# METHOD 5: readMessagesFromSocket()

Current stub:

```java
while (running.get() && !socket.isClosed()) {
    Thread.sleep(1000);
}
```

Day 3 implementation will:

1. Read bytes from InputStream
2. Deserialize into Message
3. Invoke messageHandler callback
4. Handle disconnections

---

# COMPLETE FLOW DIAGRAM

```
Node 1 starts
    ↓
Creates NetworkManager(port=8001)
    ↓
networkManager.start()
    ↓
    ├─ startServerListener()
    │    └─ Opens ServerSocket
    │    └─ accept() loop
    │
    └─ connectToAllNodes()
         ├─ connectToNode(2)
         │    ├─ Create Socket
         │    ├─ Send ID
         │    ├─ Store connection
         │    └─ Start reader thread
         │
         └─ connectToNode(3)
              ├─ Same process
```

Meanwhile:

Node 2 and Node 3 execute the same logic simultaneously.

---

# FINAL NETWORK STATE

* Node 1 connected to Node 2 and 3
* Node 2 connected to Node 1 and 3
* Node 3 connected to Node 1 and 2
* Exactly one socket per pair
* All duplicates closed

---

# SAMPLE OUTPUT EXPLAINED

```
Node 1 connecting to Node 3 at localhost:8003
```

→ Active connect attempt.

```
Node 3 listening on port 8003
```

→ ServerSocket opened.

```
Node 3 accepted connection from /127.0.0.1:56655
```

→ accept() returned.

```
Node 1 connected to node 3
```

→ Socket successfully created.

```
Node 3 identified incoming connection from node 1
```

→ Read 4-byte node ID.

```
Node 3 already connected to Node 1, closing duplicate
```

→ Duplicate detection logic.

```
Read messages from node 3
```

→ Reader thread started.