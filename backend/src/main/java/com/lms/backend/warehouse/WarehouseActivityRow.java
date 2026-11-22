package com.lms.backend.warehouse;

import java.time.Instant;

/**
 * What it does: Describes one dated entry in a scan log or an order timeline.
 */
public record WarehouseActivityRow(
        Instant time,
        String orderReference,
        String kind,
        String text) {
}
