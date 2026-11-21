package com.lms.backend.admin.order;

import java.math.BigDecimal;
import java.time.Instant;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderPaymentRow(
        Long paymentID,
        BigDecimal amount,
        String paymentStatus,
        Instant createdTime,
        Instant checkoutExpiryTime,
        Instant acceptedTime,
        Instant endedTime) {
}
