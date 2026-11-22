package com.lms.backend.admin.delivery;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class AdminDeliveryCaseRule {

    // ===== SECTION 1: CONSTANTS =====

    public static final int ADMIN_REMINDER_MINIMUM_HOURS_BETWEEN = 24;

    /**
     * What it does: Keeps the pure rules on the class.
     * Input: None.
     * Output: No object is needed.
     */
    private AdminDeliveryCaseRule() {
    }

    // ===== SECTION 2: adminCountDaysSinceReturn =====

    /**
     * What it does: Counts whole days since the package was received.
     * Input: The saved return time and the current time.
     * Output: The number of complete 24-hour periods since receipt.
     */
    public static long adminCountDaysSinceReturn(Instant adminCountDaysSinceReturnReturnedTime,
            Instant adminCountDaysSinceReturnNow) {
return Math.max(0, ChronoUnit.DAYS.between(
                adminCountDaysSinceReturnReturnedTime, adminCountDaysSinceReturnNow));
    }

    // ===== SECTION 3: adminCheckRepeatedFailure =====

    /**
     * What it does: Checks if the order reached the delivery review threshold.
     * Input: Its number of failed attempts and the review setting.
     * Output: True when the number reaches or exceeds the setting.
     */
    public static boolean adminCheckRepeatedFailure(long adminCheckRepeatedFailureCount,
            int adminCheckRepeatedFailureReviewFailureCount) {
        return adminCheckRepeatedFailureCount >= adminCheckRepeatedFailureReviewFailureCount;
    }

    // ===== SECTION 4: adminFindWaitingFlag =====

    /**
     * What it does: Chooses the advisory flag for a returned package with no request.
     * Input: Return and request facts, elapsed days and the two waiting thresholds.
     * Output: Final review, reminder due or no flag.
     */
    public static AdminReviewWaitingFlag adminFindWaitingFlag(boolean adminFindWaitingFlagIsReturned,
            boolean adminFindWaitingFlagHasPendingRequest, long adminFindWaitingFlagDaysSinceReturn,
            int adminFindWaitingFlagReminderDays, int adminFindWaitingFlagFinalReviewDays) {
        if (!adminFindWaitingFlagIsReturned || adminFindWaitingFlagHasPendingRequest) {
            return AdminReviewWaitingFlag.NONE;
        }
        // Final review takes priority once the case reaches both waiting thresholds.
        if (adminFindWaitingFlagDaysSinceReturn >= adminFindWaitingFlagFinalReviewDays) {
            return AdminReviewWaitingFlag.FINAL_REVIEW;
        }
        if (adminFindWaitingFlagDaysSinceReturn >= adminFindWaitingFlagReminderDays) {
            return AdminReviewWaitingFlag.REMINDER_DUE;
        }
        return AdminReviewWaitingFlag.NONE;
    }
}
