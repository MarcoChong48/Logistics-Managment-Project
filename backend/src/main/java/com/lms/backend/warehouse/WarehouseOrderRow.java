package com.lms.backend.warehouse;

import java.time.Instant;

/**
 * What it does: Describes one order in a Warehouse list without any client or delivery details.
 */
public record WarehouseOrderRow(
        String orderReference,
        String orderStatus,
        Instant createdTime,
        int unitsOrdered,
        int unitsPicked,
        boolean isMine,
        String remainingItemText) {
}
