package com.lms.backend.admin.placeholder;

/** F6.7: A new refund, an existing refund, or no refund when no positive payment was accepted. */
public record AdminRequestFullRefundAnswer(Long refundID, boolean adminRequestFullRefundIsNew) {
}
