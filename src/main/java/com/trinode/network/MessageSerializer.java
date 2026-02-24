package com.trinode.network;

import java.io.*;

// Java serialization includes the entire class structure in every message
public class MessageSerializer {

    public static byte[] serialize(Message message) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);

        objectStream.writeObject(message);
        objectStream.flush();

        return byteStream.toByteArray();
    }

    public static Message deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
        ObjectInputStream objectStream = new ObjectInputStream(byteStream);

        return (Message) objectStream.readObject();
    }
}
