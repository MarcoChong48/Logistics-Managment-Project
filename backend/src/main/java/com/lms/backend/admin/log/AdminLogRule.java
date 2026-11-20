package com.lms.backend.admin.log;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public final class AdminLogRule {

    // ===== SECTION 1: RETENTION AND SCHEDULE =====

    public static final int ADMIN_SECURITY_LOG_KEEP_DAYS = 90;
    public static final int ADMIN_PICKING_SCAN_HISTORY_KEEP_DAYS = 90;
    public static final int ADMIN_AUDIT_LOG_KEEP_YEARS = 3;
    public static final int ADMIN_LOG_CLEANUP_FIRST_DELAY_MINUTES = 5;
    public static final int ADMIN_LOG_CLEANUP_REPEAT_HOURS = 24;

    /**
     * What it does: Keeps the pure rules from being created as an object.
     * Input: None.
     * Output: None.
     */
    private AdminLogRule() {
    }

    // ===== SECTION 2: CUT-OFF TIMES =====

    /**
     * What it does: Finds the earliest security entry time that clean-up should keep.
     * Input: The current instant.
     * Output: The instant 90 days earlier.
     */
    public static Instant adminFindSecurityLogCutOffTime(Instant adminFindSecurityLogCutOffTimeNow) {
        return adminFindSecurityLogCutOffTimeNow.minus(ADMIN_SECURITY_LOG_KEEP_DAYS, ChronoUnit.DAYS);
    }

    /**
     * What it does: Finds the earliest picking scan time that clean-up normally keeps.
     * Input: The current instant.
     * Output: The instant 90 days earlier.
     */
    public static Instant adminFindPickingScanHistoryCutOffTime(Instant adminFindPickingScanHistoryCutOffTimeNow) {
        return adminFindPickingScanHistoryCutOffTimeNow.minus(ADMIN_PICKING_SCAN_HISTORY_KEEP_DAYS, ChronoUnit.DAYS);
    }

    /**
     * What it does: Finds the earliest audit entry time that clean-up should keep.
     * Input: The current instant.
     * Output: The instant three calendar years earlier in UTC.
     */
    public static Instant adminFindAuditLogCutOffTime(Instant adminFindAuditLogCutOffTimeNow) {
        return adminFindAuditLogCutOffTimeNow.atOffset(ZoneOffset.UTC)
                .minusYears(ADMIN_AUDIT_LOG_KEEP_YEARS).toInstant();
    }
}
