package com.lms.backend.client.product;

import java.math.BigDecimal;

public record ClientProductResponse(Long productId, String sku, String name, BigDecimal currentPriceHkd,
        int availableStock) {
}
