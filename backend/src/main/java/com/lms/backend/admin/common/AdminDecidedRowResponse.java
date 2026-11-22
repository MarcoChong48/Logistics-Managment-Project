package com.lms.backend.admin.common;

import java.time.Instant;

/** The stored outcome for the row after an Admin decision. */
public record AdminDecidedRowResponse(
        String message,
        String adminDecidedRowStateWord,
        String adminDecidedRowWorkerUsername,
        String adminDecidedRowDecidedByUsername,
        Instant adminDecidedRowDecidedTime,
        Long refundID,
        boolean adminDecidedRowRefundIsNew) {
}
