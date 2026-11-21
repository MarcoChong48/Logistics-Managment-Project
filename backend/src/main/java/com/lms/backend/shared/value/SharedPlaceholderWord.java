package com.lms.backend.shared.value;

// PLACEHOLDER INPUT PI-05
// From: The other LMS roles.
// What: Provisional words stored for payment, picking, pickup, custody and reservations.
// How to use: Replace the values here when the team confirms its shared words.
public final class SharedPlaceholderWord {

    // ===== SECTION 1: STAGE 2 WORDS =====

    public static final String SHARED_PAYMENT_STATUS_ACCEPTED = "ACCEPTED";
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
    public static final String SHARED_RESERVATION_STATUS_CONSUMED = "CONSUMED";

    // ===== SECTION 2: STAGE 5 WORDS =====

    public static final String SHARED_REFUND_STATUS_REQUESTED = "REQUESTED";
    public static final String SHARED_REFUND_STATUS_FAILED = "FAILED";
    public static final String SHARED_CUSTODY_EVENT_RETURN_REQUESTED = "RETURN_REQUESTED";
    public static final String SHARED_CUSTODY_EVENT_RETURN_RECEIVED = "RETURN_RECEIVED";
    public static final String SHARED_ATTEMPT_OUTCOME_IN_PROGRESS = "IN_PROGRESS";
    public static final String SHARED_ATTEMPT_OUTCOME_DELIVERED = "DELIVERED";
    public static final String SHARED_ATTEMPT_OUTCOME_FAILED = "FAILED";
    public static final String SHARED_FAILURE_REASON_CLIENT_NOT_HOME = "CLIENT_NOT_HOME";
    public static final String SHARED_FAILURE_REASON_WRONG_ADDRESS = "WRONG_ADDRESS";
    public static final String SHARED_FAILURE_REASON_CLIENT_REFUSED = "CLIENT_REFUSED";
    public static final String SHARED_FAILURE_REASON_UNABLE_TO_CONTACT = "UNABLE_TO_CONTACT";
    public static final String SHARED_FAILURE_REASON_OTHER = "OTHER";
    public static final String SHARED_INSTRUCTION_TYPE_REDELIVER = "REDELIVER";
    public static final String SHARED_INSTRUCTION_STATUS_PENDING = "PENDING";
    public static final String SHARED_INSTRUCTION_STATUS_APPROVED = "APPROVED";
    public static final String SHARED_INSTRUCTION_STATUS_REJECTED = "REJECTED";
    public static final String SHARED_RESERVATION_STATUS_RELEASED = "RELEASED";

    // ===== SECTION 3: STAGE 6 WORDS =====

    public static final String SHARED_PICKING_TASK_STATUS_CANCELLED = "CANCELLED";
    public static final String SHARED_MOVEMENT_TYPE_RESERVATION_RELEASED = "RESERVATION_RELEASED";

    /**
     * What it does: Keeps the shared words on the class.
     * Input: None.
     * Output: No object is needed.
     */
    private SharedPlaceholderWord() {
    }
}
