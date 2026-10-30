package com.monitoring.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record LogEntry(String requestId, String serviceId, String operation, long durationMs,
                       CallStatus status, Instant timestamp, List<Object> params, Object response,
                       String errorMessage, String stackTraceSummary) {
    public LogEntry {
        params = params == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(params));
    }
}
