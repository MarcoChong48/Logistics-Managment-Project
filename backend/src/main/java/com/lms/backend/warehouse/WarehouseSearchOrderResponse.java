package com.lms.backend.warehouse;

import java.util.List;

/**
 * What it does: Carries one Warehouse order list and whether the list was cut at its row limit.
 */
public record WarehouseSearchOrderResponse(
        List<WarehouseOrderRow> orderRowList,
        boolean isLimited) {
}
