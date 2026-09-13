package com.demo.loadbalancer;

import com.demo.server.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class LoadBalancerTest {

    private LoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        loadBalancer = new LoadBalancer(new RoundRobinStrategy(), 5);
    }

    @Test
    void shouldCreateLoadBalancer() {
        assertNotNull(loadBalancer);
    }

    @Test
    void shouldReturnRegisteredServer() {
        loadBalancer.addServer(new Server("server-1", 10));

        assertEquals("server-1", loadBalancer.getServer());
    }

    @Test
    void shouldReturnRegisteredServersRoundRobin() {
        loadBalancer.addServer(new Server("server-1", 10));
        loadBalancer.addServer(new Server("server-2", 10));
        loadBalancer.addServer(new Server("server-3", 10));

        assertEquals("server-1", loadBalancer.getServer());
        assertEquals("server-2", loadBalancer.getServer());
        assertEquals("server-3", loadBalancer.getServer());
    }

    @Test
    void shouldReturnRegisteredServersRoundRobinWrapAround() {
        loadBalancer.addServer(new Server("server-1", 10));
        loadBalancer.addServer(new Server("server-2", 10));

        assertEquals("server-1", loadBalancer.getServer());
        assertEquals("server-2", loadBalancer.getServer());
        assertEquals("server-1", loadBalancer.getServer());
    }

    @Test
    void shouldThrowExceptionWhenServerListEmpty() {
        assertThrows(IllegalStateException.class, loadBalancer::getServer);
    }

    @Test
    void shouldThrowExceptionWhenRegisteringDuplicateServer() {
        loadBalancer.addServer(new Server("server-1", 10));
        assertThrows(IllegalArgumentException.class, () -> loadBalancer.addServer(new Server("server-1", 10)));
    }

    @Test
    void shouldRemoveRegisteredServer() {
        loadBalancer.addServer(new Server("server-1", 10));
        loadBalancer.addServer(new Server("server-2", 10));
        loadBalancer.removeServer("server-1");

        assertEquals("server-2", loadBalancer.getServer());
    }

    @Test
    void shouldThrowExceptionWhenRemovingInvalidServer() {
        loadBalancer.addServer(new Server("server-1", 10));

        assertThrows(IllegalArgumentException.class, () -> loadBalancer.removeServer("server-2"));
    }

    @Test
    void shouldHaveNoAvailableServersAfterRemovingLastServer() {
        loadBalancer.addServer(new Server("server-1", 10));
        loadBalancer.removeServer("server-1");

        assertThrows(IllegalStateException.class, loadBalancer::getServer);
    }

    @Test
    void shouldReturnValidServerAfterRemovingOtherServer() {
        loadBalancer.addServer(new Server("server-1", 10));
        loadBalancer.addServer(new Server("server-2", 10));
        loadBalancer.addServer(new Server("server-3", 10));

        assertEquals("server-1", loadBalancer.getServer());

        loadBalancer.removeServer("server-2");

        assertEquals("server-3", loadBalancer.getServer());
    }

    @Test
    void shouldHandleConcurrentGetServerCalls() throws Exception {
        loadBalancer.addServer(new Server("server-1", 10));
        loadBalancer.addServer(new Server("server-2", 10));

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            tasks.add(loadBalancer::getServer);
        }
        List<Future<String>> results = executorService.invokeAll(tasks);

        for (Future<String> result: results) {
            String server = result.get();

            assertTrue(server.equals("server-1") || server.equals("server-2"));
        }
        executorService.shutdown();
    }

    @Test
    void shouldMaintainRoundRobinUnderConcurrency() throws Exception {
        loadBalancer.addServer(new Server("server-1", 10));
        loadBalancer.addServer(new Server("server-2", 10));

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            tasks.add(loadBalancer::getServer);
        }
        List<Future<String>> results = executorService.invokeAll(tasks);

        int server1Count = 0;
        int server2Count = 0;
        for (Future<String> result: results) {
            String server = result.get();
            if (server.equals("server-1")) {
                server1Count += 1;
            } else if (server.equals("server-2")) {
                server2Count += 1;
            }
        }

        assertEquals(500, server1Count);
        assertEquals(500, server2Count);
        executorService.shutdown();
    }

    @Test
    void shouldHandleConcurrentReadWrites() throws Exception {
        loadBalancer.addServer(new Server("server-1", 10));
        loadBalancer.addServer(new Server("server-2", 10));

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            tasks.add(loadBalancer::getServer);
        }
        tasks.add(() -> {
            loadBalancer.addServer(new Server("server-3", 10));
            return null;
        });
        tasks.add(() -> {
            loadBalancer.removeServer("server-1");
            return null;
        });
        List<Future<String>> results = executorService.invokeAll(tasks);
        for (Future<String> result: results) {
            result.get();
        }

        executorService.shutdown();
    }

    @Test
    void shouldInitializeWithRandomStrategy() {
        LoadBalancer lb = new LoadBalancer(new RandomStrategy(), 5);

        assertNotNull(lb);
    }

    @Test
    void shouldThrowExceptionWithNullStrategy() {
        assertThrows(NullPointerException.class, () -> new LoadBalancer(null, 5));
    }

    @Test
    void shouldThrowExceptionWhenExceedingServerCapacity() {
        for (int i = 0; i < 5; i++) {
            String id = "server-" + i;
            Server server = new Server(id, 10);
            loadBalancer.addServer(server);
        }

        assertThrows(IllegalStateException.class, () -> loadBalancer.addServer(new Server("server-6", 10)));
    }

    @Test
    void shouldNotReturnUnhealthyServers() {
        Server unhealthyServer = new Server("server-1", 10);
        unhealthyServer.markUnhealthy();
        loadBalancer.addServer(unhealthyServer);

        assertThrows(IllegalStateException.class, loadBalancer::getServer);
    }

    // tests specifically for LeastConnectionsStrategy
    @Test
    void shouldReturnServerWithLeastConnections() {
        LoadBalancer lb = new LoadBalancer(new LeastConnectionsStrategy(), 5);
        Server server1 = new Server("server-1", 3);
        Server server2 = new Server("server-2", 3);

        assertTrue(server1.tryAddConnection());

        lb.addServer(server1);
        lb.addServer(server2);
        String result = lb.getServer();

        assertEquals("server-2", result);
        assertEquals(1, server2.getActiveConnections());
        assertEquals(1, server1.getActiveConnections());
    }

    @Test
    void shouldNotSelectServerAtMaxCapacity() {
        LoadBalancer lb = new LoadBalancer(new LeastConnectionsStrategy(), 5);
        Server server1 = new Server("server-1", 1);
        Server server2 = new Server("server-2", 3);

        assertTrue(server1.tryAddConnection());

        lb.addServer(server1);
        lb.addServer(server2);
        String result = lb.getServer();

        assertEquals("server-2", result);
    }

    @Test
    void shouldThrowForGetServerWhenAllServersMaxCapacity() {
        LoadBalancer lb = new LoadBalancer(new LeastConnectionsStrategy(), 5);
        Server server1 = new Server("server-1", 1);
        Server server2 = new Server("server-2", 1);

        assertTrue(server1.tryAddConnection());
        assertTrue(server2.tryAddConnection());

        lb.addServer(server1);
        lb.addServer(server2);

        assertThrows(IllegalStateException.class, lb::getServer);
    }

    @Test
    void shouldNotExceedServerCapacitiesUnderConcurrentGetServerCalls() throws Exception {
        LoadBalancer lb = new LoadBalancer(new LeastConnectionsStrategy(), 5);
        Server server1 = new Server("server-1", 10);
        Server server2 = new Server("server-2", 10);

        lb.addServer(server1);
        lb.addServer(server2);

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            tasks.add(() -> {
                try {
                    return lb.getServer();
                } catch (IllegalStateException e) {
                    return null;
                }
            });
        }
        List<Future<String>> results = executorService.invokeAll(tasks);
        int successfulConnections = 0;
        for (Future<String> result : results) {
            if (result.get() != null) {
                successfulConnections += 1;
            }
        }

        assertEquals(20, successfulConnections);
        assertEquals(10, server1.getActiveConnections());
        assertEquals(10, server2.getActiveConnections());
    }
}
