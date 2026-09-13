package com.demo.loadbalancer;

import com.demo.server.Server;

import java.util.List;
import java.util.Random;

public class RandomStrategy implements LoadBalancingStrategy {
    private final Random random = new Random();

    @Override
    public String selectServer(List<Server> servers) {
        return servers.get(random.nextInt(servers.size())).getId();
    }
}
