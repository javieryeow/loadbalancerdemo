package com.demo.server;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private final String id;
    private volatile ServerStatus status;
    private final int capacity;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public Server(String id, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.id = id;
        this.capacity = capacity;
        this.status = ServerStatus.HEALTHY;
    }

    public String getId() {
        return id;
    }

    public boolean isHealthy() {
        return status == ServerStatus.HEALTHY;
    }

    public void markHealthy() {
        setStatus(ServerStatus.HEALTHY);
    }

    public void markUnhealthy() {
        setStatus(ServerStatus.UNHEALTHY);
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public boolean hasCapacity() {
        return getActiveConnections() < capacity;
    }

    public boolean tryAddConnection() {
        while (true) {
            int current = getActiveConnections();
            if (current >= capacity) {
                return false;
            }
            if (activeConnections.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public boolean tryRemoveConnection() {
        while (true) {
            int current = getActiveConnections();
            if (current <= 0) {
                return false;
            }
            if (activeConnections.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }

    private void setStatus(ServerStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Server other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
