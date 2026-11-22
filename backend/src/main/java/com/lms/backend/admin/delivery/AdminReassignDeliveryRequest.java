package com.lms.backend.admin.delivery;

/** F7.9: The new worker and the Admin's reason for transferring delivery responsibility. */
public record AdminReassignDeliveryRequest(
        Long adminReassignDeliveryNewWorkerUserID,
        String adminReassignDeliveryReason) {
}
