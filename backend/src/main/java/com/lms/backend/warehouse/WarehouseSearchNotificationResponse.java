package com.lms.backend.warehouse;

import java.util.List;

/**
 * What it does: Carries the worker's saved notifications and the recent new-order notices.
 */
public record WarehouseSearchNotificationResponse(
        List<WarehouseNotificationRow> adminRowList,
        List<WarehouseNewOrderRow> newOrderRowList) {
}
