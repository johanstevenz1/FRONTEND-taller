package com.monitoring.service;

import com.monitoring.exception.PaymentFailedException;
import com.monitoring.proxy.MicroserviceProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService extends ServiceSupport implements MicroserviceProxy<Object> {
    private final ConcurrentHashMap<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final double failureRate;

    public PaymentService(@Value("${app.payments.failure-rate:0.10}") double failureRate) {
        if (failureRate < 0.0 || failureRate > 1.0 || !Double.isFinite(failureRate)) {
            throw new IllegalArgumentException("Payment failure rate must be between 0 and 1");
        }
        this.failureRate = failureRate;
    }

    @Override
    public Object execute(String operation, Object... params) {
        simulateLatency();
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new PaymentFailedException("Simulated payment gateway failure");
        }
        return switch (operation) {
            case "processPayment" -> processPayment(stringParam(params, 0, "orderId"), numberParam(params, 1, "amount"), stringParam(params, 2, "method"));
            case "getPayment" -> getPayment(stringParam(params, 0, "paymentId"));
            case "refundPayment" -> refundPayment(stringParam(params, 0, "paymentId"));
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private Map<String, Object> processPayment(String orderId, double amount, String method) {
        if (amount <= 0) throw new IllegalArgumentException("Parameter amount must be greater than zero");
        String id = UUID.randomUUID().toString();
        Map<String, Object> payment = Map.of("paymentId", id, "orderId", orderId, "amount", amount,
                "method", method, "status", "APPROVED", "processedAt", Instant.now());
        payments.put(id, payment);
        return payment;
    }

    private Map<String, Object> getPayment(String id) {
        Map<String, Object> payment = payments.get(id);
        if (payment == null) throw new NoSuchElementException("Payment not found");
        return payment;
    }

    private Map<String, Object> refundPayment(String id) {
        return payments.compute(id, (key, payment) -> {
            if (payment == null) throw new NoSuchElementException("Payment not found");
            Map<String, Object> updated = new java.util.HashMap<>(payment);
            updated.put("status", "REFUNDED");
            return Map.copyOf(updated);
        });
    }

    @Override
    public String getServiceId() { return "payments"; }
}
