package com.monitoring.config;

import com.monitoring.proxy.LoggingProxy;
import com.monitoring.proxy.MicroserviceProxy;
import com.monitoring.service.InventoryService;
import com.monitoring.service.OrderService;
import com.monitoring.service.PaymentService;
import com.monitoring.store.LogStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

@Configuration
public class ProxyConfig {
    @Bean
    public MicroserviceProxy<Object> inventoryProxy(InventoryService service, LogStore store) {
        return new LoggingProxy<>(service, store);
    }

    @Bean
    public MicroserviceProxy<Object> ordersProxy(OrderService service, LogStore store) {
        return new LoggingProxy<>(service, store);
    }

    @Bean
    public MicroserviceProxy<Object> paymentsProxy(PaymentService service, LogStore store) {
        return new LoggingProxy<>(service, store);
    }

    @Bean
    public Map<String, MicroserviceProxy<Object>> serviceProxies(
            @Qualifier("inventoryProxy") MicroserviceProxy<Object> inventoryProxy,
            @Qualifier("ordersProxy") MicroserviceProxy<Object> ordersProxy,
            @Qualifier("paymentsProxy") MicroserviceProxy<Object> paymentsProxy) {
        return Map.of("inventory", inventoryProxy, "orders", ordersProxy, "payments", paymentsProxy);
    }
}
