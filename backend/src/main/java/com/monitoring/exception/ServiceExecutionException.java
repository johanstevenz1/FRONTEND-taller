package com.monitoring.exception;

public class ServiceExecutionException extends RuntimeException {
    private final String requestId;

    public ServiceExecutionException(String requestId, Throwable cause) {
        super(cause.getMessage(), cause);
        this.requestId = requestId;
    }

    public String getRequestId() { return requestId; }
}
