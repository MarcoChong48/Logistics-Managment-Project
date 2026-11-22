package com.lms.backend.warehouse;

import java.util.List;

/**
 * What it does: Carries the signed-in worker's own picking activity and scan errors.
 */
public record WarehouseSearchActivityResponse(
        List<WarehouseActivityRow> activityRowList,
        List<WarehouseActivityRow> scanErrorRowList) {
}
