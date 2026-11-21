package com.lms.backend.admin.order;

import java.math.BigDecimal;
import java.time.Instant;

// F5.1: public order data for the Admin screens.
public record AdminSearchOrderRow(
        Long orderID,
        String orderReference,
        Long clientUserID,
        String adminSearchOrderClientUsername,
        String orderStatus,
        Instant createdTime,
        Instant paymentDeadline,
        BigDecimal totalAmount,
        String adminSearchOrderLatestPaymentStatus,
        String adminSearchOrderLatestRefundStatus,
        boolean adminSearchOrderHasOpenCase) {
}
