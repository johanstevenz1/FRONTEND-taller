package com.monitoring.service;

import com.monitoring.exception.PaymentFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {
    @Test
    void failureRateOneAlwaysFails() {
        PaymentService service = new PaymentService(1.0);
        assertThrows(PaymentFailedException.class, () -> service.execute("processPayment", "order-1", 20, "CARD"));
    }

    @Test
    void failureRateZeroAllowsPayment() {
        PaymentService service = new PaymentService(0.0);
        Object result = service.execute("processPayment", "order-1", 20, "CARD");
        assertTrue(result.toString().contains("APPROVED"));
    }
}
