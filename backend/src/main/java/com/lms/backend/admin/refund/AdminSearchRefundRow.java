package com.lms.backend.admin.refund;

import java.math.BigDecimal;
import java.time.Instant;

// F8.1: public refund data for the work list and CSV.
public record AdminSearchRefundRow(
        Long refundID,
        Long paymentID,
        Long adminSearchRefundOrderID,
        String adminSearchRefundOrderReference,
        String adminSearchRefundOrderStatus,
        BigDecimal amount,
        String refundStatus,
        Instant createdTime,
        Instant completedTime,
        long adminSearchRefundAgeHours,
        boolean adminSearchRefundIsUnresolved,
        Instant adminSearchRefundLastCheckTime,
        String adminSearchRefundLastCheckOutcome,
        Long adminSearchRefundReplacedByRefundID) {
}
