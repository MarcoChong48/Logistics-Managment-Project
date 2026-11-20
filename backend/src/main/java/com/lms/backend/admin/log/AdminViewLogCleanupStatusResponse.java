package com.lms.backend.admin.log;

import java.time.Instant;

// The time and counts from the latest completed log clean-up.
public record AdminViewLogCleanupStatusResponse(
        boolean adminViewLogCleanupStatusHasRun,
        Instant adminViewLogCleanupStatusLastRunTime,
        int adminViewLogCleanupStatusSecurityLogDeletedCount,
        int adminViewLogCleanupStatusPickingScanHistoryDeletedCount,
        int adminViewLogCleanupStatusAuditLogDeletedCount,
        String adminViewLogCleanupStatusKeepRuleText) {
}
