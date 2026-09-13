package com.demo.loadbalancer;

import com.demo.server.Server;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinStrategy implements LoadBalancingStrategy{
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    @Override
    public String selectServer(List<Server> servers) {
        int index = Math.floorMod(currentIndex.getAndIncrement(), servers.size());

        return servers.get(index).getId();
    }
}
