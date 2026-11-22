package com.lms.backend.admin.refund;

import com.lms.backend.admin.placeholder.AdminRefundProviderOutcome;

// F8.2: the persisted refund status and the answer that the Admin can display.
public record AdminCheckRefundStatusResponse(
        Long refundID,
        String refundStatus,
        AdminRefundProviderOutcome adminCheckRefundStatusProviderOutcome,
        String adminCheckRefundStatusMessage) {
}
