package com.lms.backend.warehouse;

/**
 * What it does: Names the Warehouse actions written to the audit log.
 * Kept in the Warehouse package so the shared action list stays under its owner.
 */
public final class WarehouseAuditAction {

    // ===== SECTION 1: PICKING ACTIONS =====

    public static final String WAREHOUSE_AUDIT_ACTION_PICKING_STARTED = "PICKING_STARTED";
    public static final String WAREHOUSE_AUDIT_ACTION_PICKED_QUANTITY_LOWERED = "PICKED_QUANTITY_LOWERED";
    public static final String WAREHOUSE_AUDIT_ACTION_PACKING_CONFIRMED = "PACKING_CONFIRMED";

    // ===== SECTION 2: TARGET TYPES =====

    public static final String WAREHOUSE_AUDIT_TARGET_PICKING_TASK = "PICKING_TASK";

    /**
     * What it does: Prevents creating an instance of a constant holder.
     * Input: None.
     * Output: None.
     */
    private WarehouseAuditAction() {
    }
}
