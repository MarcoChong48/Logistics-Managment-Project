package com.lms.backend.admin.placeholder;

public interface AdminOrderCancellationPlaceholder {

    // ===== SECTION 1: ORDER AND STOCK OUTPUT =====

    /**
     * What it does: Permanently cancels an order and releases its held reservations.
     * Input: The order ID, deciding Admin ID and cancellation reason.
     * Output: No return value, or a state conflict if cancellation is refused.
     * Placeholder: PO-01. The shared order cancellation function will provide this behavior.
     */
    void adminCancelOrderPlaceholder(Long orderID, Long adminCancelOrderAdminUserID, String adminCancelOrderReason);
}
