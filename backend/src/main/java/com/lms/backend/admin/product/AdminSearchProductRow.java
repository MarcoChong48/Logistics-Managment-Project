package com.lms.backend.admin.product;

import java.math.BigDecimal;
import java.time.Instant;

import com.lms.backend.shared.value.SharedListingStatus;

public record AdminSearchProductRow(
        Long productID,
        String SKU,
        String name,
        BigDecimal currentPrice,
        int lowStockThreshold,
        SharedListingStatus listingStatus,
        Instant createdTime,
        int physicalStock,
        int reservedStock,
        int adminSearchProductUnavailableStock,
        int adminSearchProductAvailableStock,
        long adminSearchProductReservationShortfall,
        boolean adminSearchProductIsLowStock) {
}
