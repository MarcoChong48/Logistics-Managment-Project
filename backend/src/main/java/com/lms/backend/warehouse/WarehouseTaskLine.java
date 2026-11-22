package com.lms.backend.warehouse;

/**
 * What it does: Describes one order item with its ordered and picked quantities.
 */
public record WarehouseTaskLine(
        Long orderItemID,
        String sku,
        String productName,
        int orderedQuantity,
        int pickedQuantity) {
}
