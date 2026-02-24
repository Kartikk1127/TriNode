package com.trinode;

import com.trinode.network.Message;
import com.trinode.network.MessageType;

import static com.trinode.network.MessageSerializer.deserialize;
import static com.trinode.network.MessageSerializer.serialize;

public class Main {
    public static void main(String[] args) {

        System.out.println("Testing message serialization");

        Message originalMessage = new Message();

        originalMessage.setType(MessageType.HEARTBEAT);
        originalMessage.setSenderId(1);
        originalMessage.setGeneration(5);
        originalMessage.setPayload("test payload");

        try {
            // serialize
            byte[] serialized = serialize(originalMessage);
            System.out.println("Serialized message to " + serialized.length + " bytes");

            // deserialize
            Message deserializedMessage = deserialize(serialized);

            System.out.println("Original type: " + originalMessage.getType());
            System.out.println("Deserialized type : " + deserializedMessage.getType());
            System.out.println("Match: " + (originalMessage.getType() == deserializedMessage.getType()));

            System.out.println("SUCCESS");
        } catch (Exception e) {
            System.out.println("Serialization test: FAILED");
            e.printStackTrace();
        }
    }
}