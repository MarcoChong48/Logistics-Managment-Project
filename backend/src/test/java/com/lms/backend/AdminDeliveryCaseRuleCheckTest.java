package com.lms.backend;

import static com.lms.backend.admin.delivery.AdminDeliveryCaseRule.adminCheckRepeatedFailure;
import static com.lms.backend.admin.delivery.AdminDeliveryCaseRule.adminCountDaysSinceReturn;
import static com.lms.backend.admin.delivery.AdminDeliveryCaseRule.adminFindWaitingFlag;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.lms.backend.admin.delivery.AdminReviewWaitingFlag;

/** Database-free tests for the delivery case thresholds of Stage 7. */
class AdminDeliveryCaseRuleCheckTest {

    // ===== SECTION 1: DAYS AND FAILURE COUNTS =====

    /**
     * What it does: Checks the boundaries for whole days since return.
     * Input: Elapsed hours and the expected number of full days.
     * Output: The day count agrees at each boundary.
     */
    @ParameterizedTest
    @CsvSource({"23, 0", "24, 1", "71, 2", "72, 3"})
    void adminCheckDaysSinceReturn(long adminCheckDaysSinceReturnHours, long adminCheckDaysSinceReturnExpected) {
        Instant adminCheckDaysSinceReturnReceipt = Instant.parse("2026-11-12T10:00:00Z");
        Instant adminCheckDaysSinceReturnNow =
                adminCheckDaysSinceReturnReceipt.plus(adminCheckDaysSinceReturnHours, ChronoUnit.HOURS);
        assertEquals(adminCheckDaysSinceReturnExpected,
                adminCountDaysSinceReturn(adminCheckDaysSinceReturnReceipt, adminCheckDaysSinceReturnNow));
    }

    /**
     * What it does: Checks when the repeated-failure flag starts.
     * Input: Each sample failure count and expected flag with a threshold of two failures.
     * Output: True at and above the threshold only.
     */
    @ParameterizedTest
    @CsvSource({"1, false", "2, true", "3, true"})
    void adminCheckReviewThreshold(long adminCheckReviewThresholdCount, boolean adminCheckReviewThresholdExpected) {
        assertEquals(adminCheckReviewThresholdExpected, adminCheckRepeatedFailure(adminCheckReviewThresholdCount, 2));
    }

    // ===== SECTION 2: WAITING FLAGS =====

    /**
     * What it does: Checks which waiting flag applies for each returned or pending state.
     * Input: Return and request indicators, whole days since return and the expected flag.
     * Output: The flag for the three- and seven-day waiting thresholds, or none.
     */
    @ParameterizedTest
    @CsvSource({
        "false, false, 10, NONE",
        "true, true, 10, NONE",
        "true, false, 2, NONE",
        "true, false, 3, REMINDER_DUE",
        "true, false, 6, REMINDER_DUE",
        "true, false, 7, FINAL_REVIEW",
        "true, false, 30, FINAL_REVIEW"
    })
    void adminCheckWaitingFlag(boolean adminCheckWaitingFlagIsReturned, boolean adminCheckWaitingFlagHasPending,
            long adminCheckWaitingFlagDays, AdminReviewWaitingFlag adminCheckWaitingFlagExpected) {
        assertEquals(adminCheckWaitingFlagExpected, adminFindWaitingFlag(
                adminCheckWaitingFlagIsReturned, adminCheckWaitingFlagHasPending, adminCheckWaitingFlagDays, 3, 7));
    }
}
