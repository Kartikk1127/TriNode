# TriNode
Distributed primary-replica system - Leader-follower replication with failure detection and leader election

## Architecture
- 3-node cluster (1. leader, 2 followers)
- Synchronous replicaiton with harvest & yeild
- All to all heartbeats (1 second interval)
- Leader election via highest ID
- Generation numbers for split-brain prevention

## Tech stack
- Java

## Implementation Plan
- TCP communication
- Heartbeats and failure detection
- Leader election
- Write replication
- Read operations, catch-up, testing

## Project Structure
└── src
├── main
│   ├── java
│   │   └── com
│   │       └── trinode
│   │           ├── Main.java - Main entrypoint
│   │           ├── config
│   │           │   └── NodeConfig.java - reads the configurations from the node.conf file
│   │           ├── core
│   │           │   ├── Node.java - resembles the node that joins the cluster and will participate in elections
│   │           │   └── Role.java - role of the node
│   │           ├── election - handles the leader election
│   │           │   └── ElectionManager.java
│   │           ├── network - handles the communication between the nodes
│   │           │   ├── Message.java
│   │           │   ├── MessageType.java
│   │           │   └── NetworkManager.java
│   │           ├── replication - handles replication of the data from leader to followers
│   │           │   └── ReplicationManager.java
│   │           └── storage - in-memory storage for the node
│   │               └── DataStore.java
│   └── resources
│       └── node.conf
└── test
├── java
└── resources

