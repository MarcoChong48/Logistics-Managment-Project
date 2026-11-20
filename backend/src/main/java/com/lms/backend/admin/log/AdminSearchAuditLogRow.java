package com.lms.backend.admin.log;

import java.time.Instant;

// Public fields for one row of the audit log search.
public record AdminSearchAuditLogRow(
        Long auditID,
        Instant timestamp,
        Long actorUserID,
        String adminSearchAuditLogActorUsername,
        String action,
        String targetType,
        String targetID,
        String oldValue,
        String newValue,
        String result) {
}
