package com.demo.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class ServerTest {

    private Server server;

    @BeforeEach
    void setUp() {
        server = new Server("server-1", 10);
    }

    @Test
    void shouldCreateServer() {
        assertNotNull(server);
    }

    @Test
    void shouldReturnCorrectServerId() {
        assertEquals("server-1", server.getId());
    }

    @Test
    void shouldReturnCorrectHealthStatusCheck() {
        assertTrue(server.isHealthy());
    }

    @Test
    void shouldThrowExceptionIfCapacityIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new Server("server-2", 0));
    }

    @Test
    void shouldSetHealthStatusToUnhealthy() {
        server.markUnhealthy();

        assertFalse(server.isHealthy());
    }

    @Test
    void shouldSetHealthStatusToHealthy() {
        server.markUnhealthy();

        assertFalse(server.isHealthy());

        server.markHealthy();

        assertTrue(server.isHealthy());
    }

    @Test
    void shouldReturnTrueForSameIdServers() {
        Server other = new Server("server-1", 10);
        assertEquals(server, other);
    }

    @Test
    void shouldReturnFalseForDifferentIdServers() {
        Server other = new Server("server-2", 10);
        assertNotEquals(server, other);
    }

    @Test
    void shouldAddConnectionWhenCapacityAvailable() {
        boolean result = server.tryAddConnection();

        assertTrue(result);
        assertTrue(server.hasCapacity());
        assertEquals(1, server.getActiveConnections());
    }

    @Test
    void shouldNotAddConnectionWhenAtCapacity() {
        for (int i = 0; i < 10; i++) {
            server.tryAddConnection();
        }
        boolean result = server.tryAddConnection();

        assertFalse(result);
        assertFalse(server.hasCapacity());
        assertEquals(10, server.getActiveConnections());
    }

    @Test
    void shouldUseCapacityProvidedToConstructor() {
        Server serverWithCustomCapacity = new Server("server-2", 2);

        assertTrue(serverWithCustomCapacity.tryAddConnection());
        assertTrue(serverWithCustomCapacity.tryAddConnection());
        assertFalse(serverWithCustomCapacity.tryAddConnection());
        assertFalse(serverWithCustomCapacity.hasCapacity());
    }

    @Test
    void shouldDecreaseActiveConnections() {
        boolean addResult = server.tryAddConnection();
        boolean removeResult = server.tryRemoveConnection();

        assertTrue(addResult);
        assertTrue(removeResult);
        assertEquals(0, server.getActiveConnections());
    }

    @Test
    void shouldNotExceedCapacityUnderConcurrentAddConnections() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tasks.add(server::tryAddConnection);
        }
        List<Future<Boolean>> results = executorService.invokeAll(tasks);
        int successfulConnections = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successfulConnections += 1;
            }
        }

        assertEquals(10, successfulConnections);
        assertEquals(10, server.getActiveConnections());

        executorService.shutdown();
    }

    @Test
    void shouldNotRemoveWhenNoActiveConnections() {
        boolean result = server.tryRemoveConnection();

        assertFalse(result);
        assertEquals(0, server.getActiveConnections());
    }

    @Test
    void shouldNotHaveNegativeConnectionsUnderConcurrentRemoveConnections() throws Exception {
        for (int i = 0; i < 10; i++) {
            server.tryAddConnection();
        }
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tasks.add(server::tryRemoveConnection);
        }
        List<Future<Boolean>> results = executorService.invokeAll(tasks);
        int removedConnections = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                removedConnections += 1;
            }
        }

        assertEquals(10, removedConnections);
        assertEquals(0, server.getActiveConnections());

        executorService.shutdown();
    }
}
