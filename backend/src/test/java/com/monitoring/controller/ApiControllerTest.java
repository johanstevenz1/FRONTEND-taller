package com.monitoring.controller;

import com.monitoring.exception.GlobalExceptionHandler;
import com.monitoring.proxy.LoggingProxy;
import com.monitoring.proxy.MicroserviceProxy;
import com.monitoring.service.InventoryService;
import com.monitoring.service.OrderService;
import com.monitoring.service.PaymentService;
import com.monitoring.store.LogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiControllerTest {
    private LogStore store;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        store = new LogStore();
        Map<String, MicroserviceProxy<Object>> proxies = Map.of(
                "inventory", new LoggingProxy<>(new InventoryService(), store),
                "orders", new LoggingProxy<>(new OrderService(), store),
                "payments", new LoggingProxy<>(new PaymentService(0), store));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ServiceController(proxies), new MetricsController(store, proxies), new HealthController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void serviceCallsAreLoggedAndMetricsEndpointsReturnJson() throws Exception {
        mockMvc.perform(post("/api/services/inventory/checkStock")
                        .contentType("application/json").content("{\"params\":[\"P-100\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.result.productId").value("P-100"));
        mockMvc.perform(get("/api/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get("/api/metrics/logs").param("service", "inventory").param("status", "SUCCESS")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void failedServiceCallReturnsBadRequestAndIsLogged() throws Exception {
        mockMvc.perform(post("/api/services/inventory/unknownOperation").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(1, store.all().size());
    }

    @Test
    void invalidStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/metrics/logs").param("status", "BROKEN"))
                .andExpect(status().isBadRequest());
    }
}
