package com.trinode.network;

import java.io.Serializable;

public class Message implements Serializable {
    private MessageType type;
    private int senderId;
    private int generation;
    private Object payload;

    // we add serial version id because in case of serialization, let's say you serialize a message object today and tomorrow you add a new field to this class, if you try to deserialize the bytes to object it'll throw invalid class exception
    private static final long serialVersionID = 1L;

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public int getGeneration() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
