package com.trinode.storage;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataStore {
    private final Map<String, String> data;
    private final ReadWriteLock lock;

    public DataStore() {
        this.data = new TreeMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void put(String key, String value) {
        lock.writeLock().lock();
        try {
            data.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String get(String key) {
        lock.readLock().lock();
        try {
            return data.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void delete(String key) {
        lock.writeLock().lock();
        try {
            data.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, String> getAll() {
        lock.readLock().lock();
        try {
            return new TreeMap<>(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void replaceAll(Map<String, String> newData) {
        lock.writeLock().lock();
        try {
            data.clear();
            data.putAll(newData);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return data.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            data.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
