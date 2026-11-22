package com.lms.backend.admin.delivery;

import java.time.Instant;

/** The public facts used by one failed delivery case row and the CSV. */
public record AdminSearchFailedDeliveryCaseRow(
        Long orderID,
        String orderReference,
        long adminSearchFailedDeliveryCaseFailureCount,
        String adminSearchFailedDeliveryCaseLatestFailureReason,
        Instant adminSearchFailedDeliveryCaseLatestFailureTime,
        String adminSearchFailedDeliveryCaseLatestInternalNote,
        String adminSearchFailedDeliveryCaseLatestWorkerUsername,
        boolean adminSearchFailedDeliveryCaseIsReturned,
        Instant adminSearchFailedDeliveryCaseReturnedTime,
        Long adminSearchFailedDeliveryCaseDaysSinceReturn,
        Long adminSearchFailedDeliveryCasePendingInstructionID,
        String adminSearchFailedDeliveryCasePendingInstructionType,
        String adminSearchFailedDeliveryCasePendingInstructionSource,
        Instant adminSearchFailedDeliveryCasePendingInstructionSubmittedTime,
        boolean adminSearchFailedDeliveryCaseIsRepeatedFailure,
        AdminReviewWaitingFlag adminSearchFailedDeliveryCaseWaitingFlag,
        Instant adminSearchFailedDeliveryCaseLastReminderTime,
        Instant adminSearchFailedDeliveryCaseNextReminderTime,
        boolean adminSearchFailedDeliveryCaseHasOpenShortageCase,
        boolean adminSearchFailedDeliveryCaseOrderIsPaid) {
}
