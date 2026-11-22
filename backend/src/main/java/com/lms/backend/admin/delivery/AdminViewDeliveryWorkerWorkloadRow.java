package com.lms.backend.admin.delivery;

/** F7.8: One active Delivery Worker and their held and responsible package counts. */
public record AdminViewDeliveryWorkerWorkloadRow(
        Long userID,
        String username,
        long adminViewDeliveryWorkerWorkloadHeldPackageCount,
        long adminViewDeliveryWorkerWorkloadResponsiblePackageCount) {
}
