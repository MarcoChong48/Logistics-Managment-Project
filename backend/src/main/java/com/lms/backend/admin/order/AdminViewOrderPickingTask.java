package com.lms.backend.admin.order;

import java.time.Instant;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderPickingTask(
        Long pickingTaskID,
        String taskStatus,
        Instant startTime,
        Instant confirmPackingTime) {
}
