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
