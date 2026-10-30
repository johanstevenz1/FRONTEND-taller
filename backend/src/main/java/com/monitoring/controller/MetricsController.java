package com.monitoring.controller;

import com.monitoring.model.CallStatus;
import com.monitoring.model.LogEntry;
import com.monitoring.model.PageResponse;
import com.monitoring.model.ServiceSummary;
import com.monitoring.proxy.MicroserviceProxy;
import com.monitoring.store.LogStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    private static final List<String> SERVICE_IDS = List.of("inventory", "orders", "payments");
    private final LogStore logStore;
    private final Map<String, MicroserviceProxy<Object>> proxies;

    public MetricsController(LogStore logStore,
                             @Qualifier("serviceProxies") Map<String, MicroserviceProxy<Object>> proxies) {
        this.logStore = logStore;
        this.proxies = proxies;
    }

    @GetMapping("/summary")
    public List<ServiceSummary> summary() {
        List<LogEntry> logs = logStore.all();
        return SERVICE_IDS.stream().map(serviceId -> {
            List<LogEntry> serviceLogs = logs.stream().filter(log -> log.serviceId().equals(serviceId)).toList();
            long total = serviceLogs.size();
            long errors = serviceLogs.stream().filter(log -> log.status() == CallStatus.ERROR).count();
            long successes = total - errors;
            double errorRate = total == 0 ? 0 : roundTwo((double) errors * 100 / total);
            double successRate = total == 0 ? 0 : roundTwo((double) successes * 100 / total);
            double average = total == 0 ? 0 : roundTwo(serviceLogs.stream().mapToLong(LogEntry::durationMs).average().orElse(0));
            return new ServiceSummary(serviceId, total, successes, errors, errorRate, successRate, average);
        }).toList();
    }

    @GetMapping("/logs")
    public PageResponse<LogEntry> logs(@RequestParam(required = false) String service,
                                       @RequestParam(required = false) CallStatus status,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        return logStore.findAll(service, status, from, to, page, Math.min(size, 100));
    }

    @PostMapping("/simulate-load")
    public Map<String, Integer> simulateLoad() {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        List<Runnable> calls = new ArrayList<>(50);
        for (int i = 0; i < 50; i++) {
            String serviceId = SERVICE_IDS.get(ThreadLocalRandom.current().nextInt(SERVICE_IDS.size()));
            String operation = switch (serviceId) {
                case "inventory" -> "reserveStock";
                case "orders" -> "createOrder";
                default -> "processPayment";
            };
            Object[] params = switch (serviceId) {
                case "inventory" -> new Object[]{"P-" + (100 + 100 * ThreadLocalRandom.current().nextInt(5)), ThreadLocalRandom.current().nextInt(1, 101)};
                case "orders" -> new Object[]{"C-" + ThreadLocalRandom.current().nextInt(1, 20), "P-" + (100 + 100 * ThreadLocalRandom.current().nextInt(5)), ThreadLocalRandom.current().nextInt(1, 5)};
                default -> new Object[]{"ORD-" + ThreadLocalRandom.current().nextInt(1, 1000), ThreadLocalRandom.current().nextDouble(10, 500), "CARD"};
            };
            calls.add(() -> {
                try {
                    proxies.get(serviceId).execute(operation, params);
                    success.incrementAndGet();
                } catch (Exception exception) {
                    errors.incrementAndGet();
                }
            });
        }
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<?>> futures = calls.stream().map(executor::submit).toList();
            for (java.util.concurrent.Future<?> future : futures) future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Load simulation was interrupted", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Load simulation failed", exception.getCause());
        } finally {
            executor.shutdownNow();
        }
        return Map.of("executed", 50, "success", success.get(), "errors", errors.get());
    }

    private double roundTwo(double value) { return Math.round(value * 100.0) / 100.0; }
}
