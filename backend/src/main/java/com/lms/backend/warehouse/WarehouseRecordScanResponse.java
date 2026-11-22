package com.lms.backend.warehouse;

/**
 * What it does: Carries the saved scan result, its message for the page and the updated task.
 */
public record WarehouseRecordScanResponse(
        String result,
        String message,
        WarehouseViewPickingTaskResponse task) {
}
