package com.lms.backend.shared.value;

// PLACEHOLDER INPUT PI-05
// From: The other LMS roles.
// What: Provisional words stored for payment, picking, pickup, custody and reservations.
// How to use: Replace the values here when the team confirms its shared words.
public final class SharedPlaceholderWord {

    // ===== SECTION 1: STAGE 2 WORDS =====

    public static final String SHARED_PAYMENT_STATUS_PENDING = "PENDING";
    public static final String SHARED_PAYMENT_STATUS_ACCEPTED = "ACCEPTED";
    public static final String SHARED_PAYMENT_STATUS_ENDED_UNPAID = "ENDED_UNPAID";
    public static final String SHARED_PAYMENT_STATUS_REFUND_REQUIRED = "REFUND_REQUIRED";
    public static final String SHARED_PICKING_TASK_STATUS_PENDING = "PENDING";
    public static final String SHARED_PICKING_TASK_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String SHARED_PICKING_TASK_STATUS_PACKED = "PACKED";
    public static final String SHARED_PICKUP_LIST_STATUS_OPEN = "OPEN";
    public static final String SHARED_PICKUP_LIST_STATUS_CLOSED = "CLOSED";
    public static final String SHARED_COLLECTION_OUTCOME_PENDING = "PENDING";
    public static final String SHARED_COLLECTION_OUTCOME_COLLECTED = "COLLECTED";
    public static final String SHARED_CUSTODY_STATUS_WAREHOUSE = "WAREHOUSE";
    public static final String SHARED_CUSTODY_STATUS_DELIVERY = "DELIVERY";
    public static final String SHARED_CUSTODY_EVENT_PICKED_UP = "PICKED_UP";
    public static final String SHARED_RESERVATION_STATUS_HELD = "HELD";
    public static final String SHARED_RESERVATION_STATUS_RELEASED = "RELEASED";
    public static final String SHARED_RESERVATION_STATUS_CONSUMED = "CONSUMED";

    /**
     * What it does: Keeps the shared words on the class.
     * Input: None.
     * Output: No object is needed.
     */
    private SharedPlaceholderWord() {
    }
}
