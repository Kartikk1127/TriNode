package com.trinode.network;

public enum MessageType {
    HEARTBEAT,
    HEARTBEAT_ACK,
    ELECTION_REQUEST,
    ELECTION_RESPONSE,
    REPLICATE_DATA,
    REPLICATE_ACK
}
