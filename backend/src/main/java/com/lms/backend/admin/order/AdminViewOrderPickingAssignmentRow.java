package com.lms.backend.admin.order;

import java.time.Instant;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderPickingAssignmentRow(
        Long assignmentID,
        Long workerUserID,
        String adminViewOrderPickingAssignmentWorkerUsername,
        Instant assignedTime,
        Instant endedTime) {
}
