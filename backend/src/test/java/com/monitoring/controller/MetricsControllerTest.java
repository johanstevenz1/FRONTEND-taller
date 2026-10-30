package com.monitoring.controller;

import org.junit.jupiter.api.Test;
import com.monitoring.proxy.LoggingProxy;
import com.monitoring.proxy.MicroserviceProxy;
import com.monitoring.service.InventoryService;
import com.monitoring.service.OrderService;
import com.monitoring.service.PaymentService;
import com.monitoring.store.LogStore;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsControllerTest {
    private final LogStore store = new LogStore();
    private final MetricsController controller = new MetricsController(store, proxies());

    private Map<String, MicroserviceProxy<Object>> proxies() {
        return Map.of(
                "inventory", new LoggingProxy<>(new InventoryService(), store),
                "orders", new LoggingProxy<>(new OrderService(), store),
                "payments", new LoggingProxy<>(new PaymentService(0), store));
    }

    @Test
    void summaryAlwaysContainsThreeServices() {
        assertEquals(3, controller.summary().size());
    }

    @Test
    void logsApplyStatusAndPaginationFilters() {
        Map<String, MicroserviceProxy<Object>> proxyMap = proxies();
        proxyMap.get("inventory").execute("checkStock", "P-100");
        var page = controller.logs("inventory", com.monitoring.model.CallStatus.SUCCESS, null, null, 0, 1);
        assertEquals(1, page.content().size());
        assertEquals(1, page.totalElements());
        assertEquals(1, page.size());
        assertEquals(0, page.page());
    }

    @Test
    void simulateLoadExecutesExactlyFiftyCalls() {
        Map<String, Integer> result = controller.simulateLoad();
        assertEquals(50, result.get("executed"));
        assertEquals(50, result.get("success") + result.get("errors"));
        assertTrue(result.get("success") >= 0);
    }
}
