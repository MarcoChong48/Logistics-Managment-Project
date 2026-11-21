package com.lms.backend.admin.placeholder;

import java.util.Optional;

public interface AdminRefundPlaceholder {

    // ===== SECTION 1: PAYMENT OUTPUT =====

    /**
     * What it does: Requests a full refund for an order's accepted payment.
     * Input: The order ID, deciding Admin ID and refund reason.
     * Output: The saved refund ID, or empty when there is no payment to refund.
     * Placeholder: PO-02. The payment provider integration will provide this behavior.
     */
    Optional<Long> adminRequestFullRefundPlaceholder(Long orderID, Long adminRequestFullRefundAdminUserID,
            String adminRequestFullRefundReason);
}
