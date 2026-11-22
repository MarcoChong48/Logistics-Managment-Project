package com.lms.backend.warehouse;

import java.time.Instant;

/**
 * What it does: Describes one order that recently became ready for picking.
 */
public record WarehouseNewOrderRow(
        String orderReference,
        Instant readyTime,
        int unitsOrdered,
        String orderStatus,
        boolean isMine) {
}
