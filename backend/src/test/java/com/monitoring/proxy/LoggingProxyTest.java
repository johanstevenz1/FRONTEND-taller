package com.monitoring.proxy;

import com.monitoring.exception.ServiceExecutionException;
import com.monitoring.model.CallStatus;
import com.monitoring.store.LogStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoggingProxyTest {
    @Test
    void recordsSuccessfulCall() {
        LogStore store = new LogStore();
        MicroserviceProxy<String> delegate = delegate((operation, params) -> "done", "sample");
        LoggingProxy<String> proxy = new LoggingProxy<>(delegate, store);

        assertEquals("done", proxy.execute("run", "input"));
        var entry = store.all().get(0);
        assertEquals(CallStatus.SUCCESS, entry.status());
        assertTrue(entry.durationMs() >= 0);
        assertDoesNotThrow(() -> UUID.fromString(entry.requestId()));
        assertNotNull(entry.timestamp());
    }

    @Test
    void recordsFailureAndRethrowsWithRequestId() {
        LogStore store = new LogStore();
        MicroserviceProxy<String> delegate = delegate((operation, params) -> { throw new IllegalArgumentException("invalid input"); }, "sample");
        LoggingProxy<String> proxy = new LoggingProxy<>(delegate, store);

        ServiceExecutionException exception = assertThrows(ServiceExecutionException.class, () -> proxy.execute("run"));
        var entry = store.all().get(0);
        assertEquals(entry.requestId(), exception.getRequestId());
        assertEquals(CallStatus.ERROR, entry.status());
        assertEquals("invalid input", entry.errorMessage());
        assertNotNull(entry.stackTraceSummary());
        assertEquals(Instant.class, entry.timestamp().getClass());
    }

    private MicroserviceProxy<String> delegate(Executor executor, String id) {
        return new MicroserviceProxy<>() {
            @Override public String execute(String operation, Object... params) { return executor.execute(operation, params); }
            @Override public String getServiceId() { return id; }
        };
    }

    @FunctionalInterface
    private interface Executor { String execute(String operation, Object... params); }
}
