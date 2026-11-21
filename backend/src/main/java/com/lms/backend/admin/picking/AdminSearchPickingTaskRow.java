package com.lms.backend.admin.picking;

import java.time.Instant;

import com.lms.backend.shared.value.SharedAccountStatus;

// F6.8: Public picking, progress and current-assignment data for one order.
public record AdminSearchPickingTaskRow(
        Long pickingTaskID,
        Long orderID,
        String adminSearchPickingTaskOrderReference,
        String taskStatus,
        Instant startTime,
        Long adminSearchPickingTaskCurrentWorkerUserID,
        String adminSearchPickingTaskCurrentWorkerUsername,
        SharedAccountStatus adminSearchPickingTaskCurrentWorkerAccountStatus,
        Instant adminSearchPickingTaskAssignedTime,
        long adminSearchPickingTaskPickedQuantityTotal,
        long adminSearchPickingTaskOrderedQuantityTotal,
        boolean adminSearchPickingTaskHasOpenShortageCase) {
}
