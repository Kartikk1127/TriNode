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
```
src/
├── main/
│   ├── java/com/trinode/
│   │   ├── Main.java                    # Entry point
│   │   ├── config/
│   │   │   └── NodeConfig.java          # Configuration reader
│   │   ├── core/
│   │   │   ├── Node.java                # Node lifecycle & state
│   │   │   └── Role.java                # LEADER/FOLLOWER enum
│   │   ├── election/
│   │   │   └── ElectionManager.java     # Leader election logic
│   │   ├── network/
│   │   │   ├── Message.java             # Message structure
│   │   │   ├── MessageType.java         # Message type enum
│   │   │   └── NetworkManager.java      # TCP communication
│   │   ├── replication/
│   │   │   └── ReplicationManager.java  # Data replication
│   │   └── storage/
│   │       └── DataStore.java           # Thread-safe in-memory store
│   └── resources/
│       └── node.conf                    # Node addresses
└── test/
    ├── java/
    └── resources/
```
