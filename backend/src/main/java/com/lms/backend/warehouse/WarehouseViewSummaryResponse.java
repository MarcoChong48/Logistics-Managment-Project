package com.lms.backend.warehouse;

import java.util.List;

/**
 * What it does: Carries the counts shown beside the Warehouse side-menu entries.
 */
public record WarehouseViewSummaryResponse(
        int availableOrderCount,
        int myTaskCount,
        int unreadAdminNotificationCount,
        List<String> newOrderReferenceList) {
}
