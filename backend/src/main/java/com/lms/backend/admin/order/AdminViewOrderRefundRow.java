package com.lms.backend.admin.order;

import java.math.BigDecimal;
import java.time.Instant;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderRefundRow(
        Long refundID,
        Long paymentID,
        BigDecimal amount,
        String refundStatus,
        Instant createdTime,
        Instant completedTime) {
}
