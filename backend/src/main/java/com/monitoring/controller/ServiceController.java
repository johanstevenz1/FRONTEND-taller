package com.monitoring.controller;

import com.monitoring.proxy.MicroserviceProxy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/services")
public class ServiceController {
    private final Map<String, MicroserviceProxy<Object>> proxies;

    public ServiceController(@Qualifier("serviceProxies") Map<String, MicroserviceProxy<Object>> proxies) { this.proxies = proxies; }

    @PostMapping("/{serviceId}/{operation}")
    public Map<String, Object> execute(@PathVariable String serviceId, @PathVariable String operation,
                                       @RequestBody(required = false) ExecutionRequest request) {
        MicroserviceProxy<Object> proxy = proxies.get(serviceId);
        if (proxy == null) throw new NoSuchElementException("Unknown service: " + serviceId);
        List<Object> params = request == null || request.params() == null ? List.of() : request.params();
        Object result = proxy.execute(operation, params.toArray());
        return Map.of("status", "SUCCESS", "result", result);
    }

    public record ExecutionRequest(List<Object> params) { }
}
