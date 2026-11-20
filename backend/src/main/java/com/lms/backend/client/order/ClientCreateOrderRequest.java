package com.lms.backend.client.order;

import java.math.BigDecimal;
import java.util.List;

public record ClientCreateOrderRequest(List<Item> items, String recipientName, String contactPhone,
        String deliveryAddress) {

    // Preserve numeric input until validation can reject fractional or out-of-range values.
    public record Item(BigDecimal productId, BigDecimal quantity) {
    }
}
