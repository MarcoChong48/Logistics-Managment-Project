package com.lms.backend.client.order;

import java.time.Instant;
import java.util.List;

import com.lms.backend.client.order.ClientOrderDetailsResponse.PaymentAttempt;

public record ClientOrderTrackingResponse(String orderReference, String orderStatus, String statusLabel,
        Instant currentStatusSince, boolean historyComplete, List<TrackingEvent> progression,
        List<DeliveryAttempt> deliveryAttempts, List<PaymentAttempt> paymentAttempts) {

    public record TrackingEvent(String type, Instant occurredAt, Integer attemptNumber, Integer cycleNumber) {
    }

    public record DeliveryAttempt(int attemptNumber, Integer cycleNumber, int deliveryAddressVersion,
            Instant startedAt, Instant endedAt, String outcome, String failureReason) {
    }
}
