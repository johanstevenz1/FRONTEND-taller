package com.monitoring.model;

public record ServiceSummary(String serviceId, long totalCalls, long successCount, long errorCount,
                             double errorRate, double successRate, double avgResponseTimeMs) { }
