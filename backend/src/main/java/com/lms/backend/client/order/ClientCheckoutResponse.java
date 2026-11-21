package com.lms.backend.client.order;

import java.math.BigDecimal;
import java.time.Instant;

public record ClientCheckoutResponse(long checkoutId, String orderReference, BigDecimal amountHkd,
        Instant checkoutExpiresAt, boolean providerCheckoutAvailable) {
}
