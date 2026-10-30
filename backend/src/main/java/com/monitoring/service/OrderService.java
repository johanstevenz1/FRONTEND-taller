package com.monitoring.service;

import com.monitoring.proxy.MicroserviceProxy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService extends ServiceSupport implements MicroserviceProxy<Object> {
    private final ConcurrentHashMap<String, Map<String, Object>> orders = new ConcurrentHashMap<>();

    @Override
    public Object execute(String operation, Object... params) {
        simulateLatency();
        return switch (operation) {
            case "createOrder" -> createOrder(stringParam(params, 0, "customerId"), stringParam(params, 1, "productId"), intParam(params, 2, "quantity"));
            case "getOrder" -> getOrder(stringParam(params, 0, "orderId"));
            case "cancelOrder" -> cancelOrder(stringParam(params, 0, "orderId"));
            case "listOrders" -> List.copyOf(orders.values());
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private Map<String, Object> createOrder(String customerId, String productId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Parameter quantity must be greater than zero");
        String id = UUID.randomUUID().toString();
        Map<String, Object> order = Map.of("orderId", id, "customerId", customerId, "productId", productId,
                "quantity", quantity, "status", "CREATED", "createdAt", Instant.now());
        orders.put(id, order);
        return order;
    }

    private Map<String, Object> getOrder(String id) {
        Map<String, Object> order = orders.get(id);
        if (order == null) throw new NoSuchElementException("Order not found");
        return order;
    }

    private Map<String, Object> cancelOrder(String id) {
        return orders.compute(id, (key, order) -> {
            if (order == null) throw new NoSuchElementException("Order not found");
            Map<String, Object> updated = new java.util.HashMap<>(order);
            updated.put("status", "CANCELLED");
            return Map.copyOf(updated);
        });
    }

    @Override
    public String getServiceId() { return "orders"; }
}
