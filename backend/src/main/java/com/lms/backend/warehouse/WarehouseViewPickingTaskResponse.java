package com.lms.backend.warehouse;

import java.time.Instant;
import java.util.List;

/**
 * What it does: Carries the picking task of one order and what the signed-in worker may do with it.
 */
public record WarehouseViewPickingTaskResponse(
        String orderReference,
        String orderStatus,
        String taskStatus,
        String assignedUsername,
        boolean isMine,
        boolean canScan,
        boolean canConfirmPacking,
        boolean hasOpenShortage,
        Instant startTime,
        Instant confirmPackingTime,
        List<WarehouseTaskLine> lineList) {
}
