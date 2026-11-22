package com.lms.backend.warehouse;

import java.util.UUID;

/**
 * What it does: Carries the lower picked quantity chosen in the Picked column.
 */
public record WarehouseLowerPickedQuantityRequest(
        Integer pickedQuantity,
        String reason,
        UUID requestId) {
}
