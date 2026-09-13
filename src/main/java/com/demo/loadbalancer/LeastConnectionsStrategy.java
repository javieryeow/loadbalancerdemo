package com.demo.loadbalancer;

import com.demo.server.Server;

import java.util.Comparator;
import java.util.List;

public class LeastConnectionsStrategy implements LoadBalancingStrategy{
    @Override
    public String selectServer(List<Server> servers) {
        Server server = servers.stream()
                .min(Comparator.comparingInt(Server::getActiveConnections))
                .orElseThrow();

        return server.getId();
    }
}
