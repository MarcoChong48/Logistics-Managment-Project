package com.lms.backend.admin.order;

import java.time.Instant;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderDeliveryAttemptRow(
        Long deliveryAttemptID,
        Long deliveryWorkerID,
        String adminViewOrderDeliveryAttemptWorkerUsername,
        Instant startTime,
        Instant endTime,
        String outcome,
        String failureReason,
        String internalNote,
        long adminViewOrderDeliveryAttemptPhotoProofCount,
        long adminViewOrderDeliveryAttemptSignatureProofCount) {
}
