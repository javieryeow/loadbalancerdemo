package com.demo.loadbalancer;

import com.demo.server.Server;

import java.util.List;

public interface LoadBalancingStrategy {
    String selectServer(List<Server> servers);
}
