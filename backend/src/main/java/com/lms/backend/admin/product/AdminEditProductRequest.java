package com.lms.backend.admin.product;

public record AdminEditProductRequest(
        String adminEditProductSKU,
        String adminEditProductName,
        String adminEditProductCurrentPrice,
        String adminEditProductLowStockThreshold) {
}
