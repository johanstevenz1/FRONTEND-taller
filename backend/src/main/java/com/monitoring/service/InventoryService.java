package com.monitoring.service;

import com.monitoring.proxy.MicroserviceProxy;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryService extends ServiceSupport implements MicroserviceProxy<Object> {
    public record Product(String id, String name, int stock, double price) { }
    private final ConcurrentHashMap<String, Product> products = new ConcurrentHashMap<>();

    public InventoryService() {
        products.put("P-100", new Product("P-100", "Laptop", 15, 1299.99));
        products.put("P-200", new Product("P-200", "Mouse", 80, 29.99));
        products.put("P-300", new Product("P-300", "Keyboard", 45, 79.99));
        products.put("P-400", new Product("P-400", "Monitor", 20, 349.99));
        products.put("P-500", new Product("P-500", "Headset", 35, 99.99));
    }

    @Override
    public Object execute(String operation, Object... params) {
        simulateLatency();
        return switch (operation) {
            case "listProducts" -> products.values().stream().sorted(Comparator.comparing(Product::id)).toList();
            case "checkStock" -> checkStock(stringParam(params, 0, "productId"));
            case "addStock" -> addStock(stringParam(params, 0, "productId"), intParam(params, 1, "quantity"));
            case "reserveStock" -> reserveStock(stringParam(params, 0, "productId"), intParam(params, 1, "quantity"));
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private Map<String, Object> checkStock(String id) {
        Product product = requireProduct(id);
        return Map.of("productId", product.id(), "name", product.name(), "stock", product.stock());
    }

    private Product addStock(String id, int quantity) {
        requireNonNegative(quantity, "quantity");
        return updateStock(id, quantity, false);
    }

    private Product reserveStock(String id, int quantity) {
        requireNonNegative(quantity, "quantity");
        return updateStock(id, quantity, true);
    }

    private Product updateStock(String id, int quantity, boolean reserve) {
        try {
            return products.compute(id, (key, product) -> {
                if (product == null) throw new NoSuchElementException("Product not found");
                if (reserve && product.stock() < quantity) throw new IllegalStateException("Insufficient stock");
                long updatedStock = reserve ? (long) product.stock() - quantity : (long) product.stock() + quantity;
                if (updatedStock > Integer.MAX_VALUE) throw new IllegalArgumentException("Stock exceeds maximum supported quantity");
                return new Product(product.id(), product.name(), (int) updatedStock, product.price());
            });
        } catch (NoSuchElementException | IllegalStateException | IllegalArgumentException exception) {
            throw exception;
        }
    }

    private Product requireProduct(String id) {
        Product product = products.get(id);
        if (product == null) throw new NoSuchElementException("Product not found");
        return product;
    }

    @Override
    public String getServiceId() { return "inventory"; }
}
