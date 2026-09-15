package com.demo.server;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private final String id;
    // volatile ensures changes made to status are immediately visible to all threads
    // read and write operations from all threads use the main memory instead of its own cpu cache
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

    /* activeConnections is a single shared counter with a simple bounded invariant:
     * it must remain between zero and capacity. CAS atomically performs the
     * conditional update without locking the entire server object. If another thread
     * modifies the counter between my read and update, the CAS fails and retries
     * with the latest value. A lock would also be correct and could be simpler
     * for more complex multi-variable state, but for this small counter update,
     * an AtomicInteger with CAS is a natural fit. */
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

    // If two objects are equal according to equals(), they must have the same hashCode().
    // the reverse may not be true (i.e. different objects can have the same hashCode)
    // assuming we are searching for an object inside a hash collection (e.g. HashSet or HashMap)
    /* hashCode()
        ↓
    find relevant bucket
        ↓
    equals()
        ↓
    find matching object within that bucket */
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
