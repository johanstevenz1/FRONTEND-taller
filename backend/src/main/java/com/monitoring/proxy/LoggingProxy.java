package com.monitoring.proxy;

import com.monitoring.exception.ServiceExecutionException;
import com.monitoring.model.CallStatus;
import com.monitoring.model.LogEntry;
import com.monitoring.store.LogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class LoggingProxy<T> implements MicroserviceProxy<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingProxy.class);
    private final MicroserviceProxy<T> delegate;
    private final LogStore logStore;

    public LoggingProxy(MicroserviceProxy<T> delegate, LogStore logStore) {
        this.delegate = delegate;
        this.logStore = logStore;
    }

    @Override
    public T execute(String operation, Object... params) {
        String requestId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        List<Object> input = params == null ? List.of() : Arrays.asList(params.clone());
        LOGGER.info("[{}] START {}.{}", requestId, getServiceId(), operation);
        try {
            T result = delegate.execute(operation, params);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logStore.add(new LogEntry(requestId, getServiceId(), operation, durationMs, CallStatus.SUCCESS,
                    Instant.now(), input, result, null, null));
            LOGGER.info("[{}] SUCCESS {}.{} durationMs={} result={}", requestId, getServiceId(), operation, durationMs, result);
            return result;
        } catch (Exception exception) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            String summary = stackTraceSummary(exception);
            try {
                logStore.add(new LogEntry(requestId, getServiceId(), operation, durationMs, CallStatus.ERROR,
                        Instant.now(), input, null, exception.getMessage(), summary));
            } catch (RuntimeException loggingFailure) {
                exception.addSuppressed(loggingFailure);
            }
            LOGGER.error("[{}] ERROR {}.{} durationMs={} error={}", requestId, getServiceId(), operation, durationMs, exception.getMessage());
            throw new ServiceExecutionException(requestId, exception);
        }
    }

    private String stackTraceSummary(Exception exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return exception.getClass().getSimpleName() + ": " + exception.getMessage() + "\n"
                + writer.toString().lines().limit(5).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    @Override
    public String getServiceId() { return delegate.getServiceId(); }
}
