package com.lms.backend.admin.placeholder;

import java.math.BigDecimal;

public interface AdminRefundPlaceholder {

    // ===== SECTION 1: PAYMENT OUTPUT =====

    /**
     * What it does: Requests a full refund for an order's latest positive accepted payment.
     * Input: The order ID, deciding Admin ID and refund reason.
     * Output: The refund ID and whether this request saved it, or an empty refund ID when nothing was paid.
     * Placeholder: PO-02. The payment provider integration will provide this behavior.
     */
    AdminRequestFullRefundAnswer adminRequestFullRefundPlaceholder(Long orderID, Long adminRequestFullRefundAdminUserID,
            String adminRequestFullRefundReason);

    /** Records initial obligations for all timestamped accepted payments after a Client cancellation. */
    void recordClientCancellationRefunds(long orderId);

    /** Records an initial obligation for a capture already verified by a trusted provider adapter. */
    void recordVerifiedCaptureRefund(long paymentId, String providerReference, BigDecimal capturedAmount);

    // ===== SECTION 2: PAYMENT INPUT =====

    /**
     * What it does: Asks the payment provider to check the original refund attempt.
     * Input: The refund ID, with its payment locked by the caller.
     * Output: The reported outcome and an optional public explanation.
     * Placeholder: PI-07. The payment integration will read the real result.
     */
    AdminRefundProviderAnswer adminCheckRefundProviderStatusPlaceholder(Long refundID);

    // ===== SECTION 3: RETRY OUTPUT =====

    /**
     * What it does: Requests a new attempt after the caller confirms failure.
     * Input: The failed refund ID, deciding Admin ID and reason, with the payment locked by the caller.
     * Output: The new refund ID.
     * Placeholder: PO-03. The payment integration will send a real refund request.
     */
    Long adminRetryRefundPlaceholder(Long refundID, Long adminRetryRefundAdminUserID, String adminRetryRefundReason);
}
