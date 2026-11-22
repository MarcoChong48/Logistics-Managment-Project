package com.lms.backend.admin.refund;

// F8.3 requires a reason before checking or retrying a refund.
public record AdminRetryRefundRequest(String adminRetryRefundReason) {
}
