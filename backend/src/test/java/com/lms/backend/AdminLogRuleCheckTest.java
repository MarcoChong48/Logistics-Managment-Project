package com.lms.backend;

import static com.lms.backend.admin.log.AdminLogRule.adminFindAuditLogCutOffTime;
import static com.lms.backend.admin.log.AdminLogRule.adminFindPickingScanHistoryCutOffTime;
import static com.lms.backend.admin.log.AdminLogRule.adminFindSecurityLogCutOffTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AdminLogRuleCheckTest {

    // ===== SECTION 1: RETENTION CUT-OFFS =====

    /**
     * What it does: Checks the security log cut-off for a known UTC instant.
     * Input: The current date and time.
     * Output: The check passes when the result is 90 days earlier.
     */
    @Test
    void adminFindSecurityLogCutOffTimeKeepsNinetyDays() {
        assertEquals(Instant.parse("2026-08-22T08:30:00Z"),
                adminFindSecurityLogCutOffTime(Instant.parse("2026-11-20T08:30:00Z")));
    }

    /**
     * What it does: Checks the picking history cut-off across February in a leap year.
     * Input: The current date and time.
     * Output: The check passes when the result is 90 days earlier.
     */
    @Test
    void adminFindPickingScanHistoryCutOffTimeKeepsNinetyDays() {
        assertEquals(Instant.parse("2024-02-29T08:30:00Z"),
                adminFindPickingScanHistoryCutOffTime(Instant.parse("2024-05-29T08:30:00Z")));
    }

    /**
     * What it does: Checks that the audit cut-off uses calendar years instead of a fixed day count.
     * Input: A leap-day date and time.
     * Output: The check passes when the result is February 28 in the target year.
     */
    @Test
    void adminFindAuditLogCutOffTimeKeepsThreeCalendarYears() {
        assertEquals(Instant.parse("2021-02-28T08:30:00Z"),
                adminFindAuditLogCutOffTime(Instant.parse("2024-02-29T08:30:00Z")));
    }

    /**
     * What it does: Checks the boundary used by the strict older-than deletion comparisons.
     * Input: A row at the expected cut-off and a row just before it.
     * Output: The check passes when only the older row qualifies for deletion.
     */
    @Test
    void adminFindSecurityLogCutOffTimeKeepsBoundaryRow() {
        Instant adminFindSecurityLogCutOffTimeBoundary = Instant.parse("2026-08-22T08:30:00Z");
        Instant adminFindSecurityLogCutOffTimeResult =
                adminFindSecurityLogCutOffTime(Instant.parse("2026-11-20T08:30:00Z"));
        assertFalse(adminFindSecurityLogCutOffTimeBoundary.isBefore(adminFindSecurityLogCutOffTimeResult));
        assertTrue(adminFindSecurityLogCutOffTimeBoundary.minusNanos(1000)
                .isBefore(adminFindSecurityLogCutOffTimeResult));
    }
}
