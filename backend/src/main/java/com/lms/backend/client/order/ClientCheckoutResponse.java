package com.lms.backend.client.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ClientCheckoutResponse(long checkoutId, String orderReference, BigDecimal amountHkd,
        Instant checkoutExpiresAt, boolean providerCheckoutAvailable,
        @JsonInclude(JsonInclude.Include.NON_NULL) String checkoutUrl) {
}
