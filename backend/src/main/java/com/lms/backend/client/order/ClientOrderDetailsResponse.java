package com.lms.backend.client.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClientOrderDetailsResponse(String orderReference, Instant createdAt, String orderStatus,
        Instant paymentDeadline, BigDecimal totalAmountHkd, List<Item> items, DeliveryDetails deliveryDetails,
        List<PaymentAttempt> paymentAttempts, boolean canCancel) {

    public record Item(String sku, String name, String labelSource, int quantity, BigDecimal unitPriceHkd) {
    }

    public record DeliveryDetails(int version, String recipientName, String phone, String address) {
    }

    public record PaymentAttempt(String status, Instant createdAt, Instant acceptedAt, List<Refund> refunds) {
    }

    public record Refund(String status, BigDecimal amountHkd, Instant requestedAt, Instant completedAt) {
    }
}
