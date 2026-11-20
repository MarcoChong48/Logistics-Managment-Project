package com.lms.backend.admin.product;

public record AdminPreviewProductImpactResponse(
        Long productID,
        String SKU,
        long adminPreviewProductImpactOpenOrderCount,
        long adminPreviewProductImpactAwaitingPaymentOrderCount) {
}
