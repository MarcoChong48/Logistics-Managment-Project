package com.lms.backend.admin.log;

import java.time.Instant;

// Public fields for one row of the security log search.
public record AdminSearchSecurityLogRow(
        Long securityLogID,
        Instant timestamp,
        Long userID,
        String adminSearchSecurityLogUsername,
        String eventType,
        String result,
        String ipAddress,
        String deviceInfo,
        String details) {
}
