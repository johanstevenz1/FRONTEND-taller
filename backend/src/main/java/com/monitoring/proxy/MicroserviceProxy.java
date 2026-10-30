package com.monitoring.proxy;

public interface MicroserviceProxy<T> {
    T execute(String operation, Object... params);
    String getServiceId();
}
