package com.monitoring.service;

import java.util.concurrent.ThreadLocalRandom;

abstract class ServiceSupport {
    protected void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(20, 301));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Service operation was interrupted", exception);
        }
    }

    protected String stringParam(Object[] params, int index, String name) {
        if (params == null || params.length <= index || params[index] == null) {
            throw new IllegalArgumentException("Missing required parameter: " + name);
        }
        if (!(params[index] instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Parameter " + name + " must be a non-empty string");
        }
        return value;
    }

    protected int intParam(Object[] params, int index, String name) {
        if (params == null || params.length <= index || params[index] == null) {
            throw new IllegalArgumentException("Missing required parameter: " + name);
        }
        if (!(params[index] instanceof Number value)) {
            throw new IllegalArgumentException("Parameter " + name + " must be a number");
        }
        double numeric = value.doubleValue();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric) || numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Parameter " + name + " must be a valid integer");
        }
        return (int) numeric;
    }

    protected double numberParam(Object[] params, int index, String name) {
        if (params == null || params.length <= index || params[index] == null) {
            throw new IllegalArgumentException("Missing required parameter: " + name);
        }
        if (!(params[index] instanceof Number value) || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException("Parameter " + name + " must be a finite number");
        }
        return value.doubleValue();
    }

    protected void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException("Parameter " + name + " must not be negative");
    }
}
