package com.lms.backend.admin.user;

import java.time.Instant;

import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

// Public account fields for one row of the Users list.
public record AdminSearchUserRow(
        Long userID,
        String username,
        SharedUserRole role,
        SharedAccountStatus accountStatus,
        int failedLoginCount,
        Instant lockUntil,
        boolean mustChangePassword,
        Instant createdTime,
        boolean adminSearchUserIsLockedNow,
        Instant adminSearchUserLastLoginTime,
        boolean adminSearchUserIsOwnAccount) {
}
