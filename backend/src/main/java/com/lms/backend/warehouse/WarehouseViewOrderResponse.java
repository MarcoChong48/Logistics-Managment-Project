package com.lms.backend.warehouse;

import java.time.Instant;
import java.util.List;

/**
 * What it does: Carries the status-level details of one order for All orders history.
 */
public record WarehouseViewOrderResponse(
        String orderReference,
        String orderStatus,
        Instant createdTime,
        List<WarehouseTaskLine> lineList,
        List<WarehouseActivityRow> timelineRowList) {
}
