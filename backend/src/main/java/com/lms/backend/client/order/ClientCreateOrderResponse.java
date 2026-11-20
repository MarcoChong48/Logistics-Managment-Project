package com.lms.backend.client.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.lms.backend.shared.value.SharedOrderStatus;

public record ClientCreateOrderResponse(String orderReference, SharedOrderStatus orderStatus,
        BigDecimal totalAmount, Instant paymentDeadline) {
}
