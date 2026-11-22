package com.lms.backend.client.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClientOrderListResponse(List<OrderSummary> orders, String nextCursor) {

    public record OrderSummary(String orderReference, Instant createdAt, String orderStatus, BigDecimal totalAmountHkd,
            Instant paymentDeadline, ItemSummary itemSummary) {
    }

    public record ItemSummary(long productCount, long totalQuantity) {
    }
}
