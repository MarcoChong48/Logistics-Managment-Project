package com.lms.backend.admin.product;

public record AdminCreateProductRequest(
        String adminCreateProductSKU,
        String adminCreateProductName,
        String adminCreateProductCurrentPrice,
        String adminCreateProductLowStockThreshold,
        String adminCreateProductListingStatus) {
}
