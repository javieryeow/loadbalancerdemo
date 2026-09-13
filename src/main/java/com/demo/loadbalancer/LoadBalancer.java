package com.demo.loadbalancer;

import com.demo.server.Server;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LoadBalancer {
    // stores the list of servers behind the load balancer
    private final List<Server> servers = new ArrayList<>();
    private final LoadBalancingStrategy strategy;
    private final int capacity;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public LoadBalancer(LoadBalancingStrategy strategy, int capacity) {
        this.strategy = Objects.requireNonNull(strategy);
        this.capacity = capacity;
    }

    // add server to server list
    public void addServer(Server server) {
        lock.writeLock().lock();
        try {
            if (servers.size() >= capacity) {
                throw new IllegalStateException("Load balancer is already at max capacity");
            }
            if (servers.contains(server)) {
                throw new IllegalArgumentException("Server already exists");
            }

            servers.add(server);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // remove a server from the server list
    public void removeServer(String id) {
        lock.writeLock().lock();
        try {
            Server server = findServer(id);
            servers.remove(server);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // selects a server to add a connection to, based on the defined load balancing strategy
    public String getServer() {
        List<Server> eligibleServers;
        // use the read lock because we are observing the mutable state of servers
        lock.readLock().lock();
        try {
            if (servers.isEmpty()) {
                throw new IllegalStateException("No existing servers");
            }
            eligibleServers = new ArrayList<>(
                    servers.stream()
                            .filter(Server::isHealthy)
                            .filter(Server::hasCapacity)
                            .toList()
            );
        } finally {
            lock.readLock().unlock();
        }

        // use retry loop rather than a write lock so we can maintain concurrency and
        // let the server object handle its active connections atomically via Atomic Integer
        while (!eligibleServers.isEmpty()) {
            String id = strategy.selectServer(eligibleServers);
            Server server = findServer(id);
            // successfully add a connection to the server
            if (server.tryAddConnection()) {
                return id;
            }
            // if connection fails, remove the server from the eligibleServers and retry
            eligibleServers.remove(server);
        }
        throw new IllegalStateException("No server capacity available. Please try again later");
    }

    // searches for a server with serverId in the load balancer's server list
    private Server findServer(String serverId) {
        return servers.stream()
                .filter(server -> server.getId().equals(serverId))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("This server does not exist"));
    }
}
