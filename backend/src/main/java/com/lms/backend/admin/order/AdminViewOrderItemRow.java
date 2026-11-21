package com.lms.backend.admin.order;

import java.math.BigDecimal;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderItemRow(
        Long orderItemID,
        Long productID,
        String adminViewOrderItemSKU,
        String adminViewOrderItemProductName,
        int quantity,
        BigDecimal unitPrice,
        int pickedQuantity,
        BigDecimal adminViewOrderItemLineTotal) {
}
