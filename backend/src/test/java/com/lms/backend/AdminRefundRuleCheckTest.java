package com.lms.backend;

import static com.lms.backend.admin.refund.AdminRefundRule.adminCheckRefundIsUnresolved;
import static com.lms.backend.admin.refund.AdminRefundRule.adminCountRefundAgeHours;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Database-free checks for the Stage 8 refund rules. */
class AdminRefundRuleCheckTest {

    // ===== SECTION 1: UNRESOLVED REFUNDS =====

    // What it does: Checks whether the latest attempt still needs attention.
    // Input: The stored status and its expected inclusion in the list.
    // Output: The expected unresolved result, including for an unfamiliar status.
    @ParameterizedTest
    @CsvSource({"REQUESTED, true", "PENDING, true", "FAILED, true", "SOMETHING_NEW, true", "REFUNDED, false"})
    void adminCheckLatestRefundIsUnresolved(String refundStatus, boolean adminCheckLatestRefundExpected) {
        assertEquals(adminCheckLatestRefundExpected, adminCheckRefundIsUnresolved(refundStatus, null));
    }

    // What it does: Checks that an older failed attempt belongs to the replacement's case.
    // Input: A failed status and the ID of the replacement.
    // Output: The replaced attempt is excluded from the unresolved list.
    @Test
    void adminCheckReplacedRefundIsResolved() {
        assertFalse(adminCheckRefundIsUnresolved("FAILED", 2L));
    }

    // ===== SECTION 2: REFUND AGE =====

    // What it does: Checks the boundaries for completed hours.
    // Input: Minutes elapsed and the expected whole hours.
    // Output: The expected whole hours for each interval.
    @ParameterizedTest
    @CsvSource({"59, 0", "60, 1", "2940, 49"})
    void adminCheckRefundAge(long adminCheckRefundAgeMinutes, long adminCheckRefundAgeExpected) {
        Instant createdTime = Instant.parse("2026-11-20T10:00:00Z");
        Instant adminCheckRefundAgeEndTime = createdTime.plus(adminCheckRefundAgeMinutes, ChronoUnit.MINUTES);
        assertEquals(adminCheckRefundAgeExpected, adminCountRefundAgeHours(createdTime, adminCheckRefundAgeEndTime));
    }
}
