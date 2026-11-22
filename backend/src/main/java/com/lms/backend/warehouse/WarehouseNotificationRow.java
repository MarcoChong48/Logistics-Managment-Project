package com.lms.backend.warehouse;

import java.time.Instant;

/**
 * What it does: Describes one notification saved for the signed-in worker.
 */
public record WarehouseNotificationRow(
        Long notificationID,
        String type,
        String message,
        Instant createdTime,
        boolean isRead,
        String orderReference,
        String orderStatus) {
}
