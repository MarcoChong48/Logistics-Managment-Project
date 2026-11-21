package com.lms.backend.admin.picking;

// F6.10: The replacement worker and the Admin's reason.
public record AdminReassignPickingTaskRequest(
        Long adminReassignPickingTaskNewWorkerUserID,
        String adminReassignPickingTaskReason) {
}
