package com.lms.backend.shared.login;

import java.time.Instant;

public final class SharedLoginRule {

    // ===== SECTION 1: CONSTANTS =====

    public static final int SHARED_LOGIN_MAXIMUM_FAILED_ATTEMPTS = 5;
    public static final int SHARED_LOGIN_LOCK_MINUTES = 15;
    public static final int SHARED_PASSWORD_MINIMUM_LENGTH = 8;
    public static final int SHARED_PASSWORD_MAXIMUM_LENGTH = 72;
    public static final int SHARED_PASSWORD_MAXIMUM_UTF8_BYTE_COUNT = 72;
    public static final int SHARED_TEMPORARY_PASSWORD_VALID_HOURS = 24;
    public static final int SHARED_SESSION_WARNING_SECONDS = 120;

    // ===== SECTION 2: CONSTRUCTOR =====

    /**
     * What it does: Keeps the static rules from being created as an object.
     * Input: None.
     * Output: None.
     */
    private SharedLoginRule() {
    }

    // ===== SECTION 3: sharedCheckLockIsActive =====

    /**
     * What it does: Checks whether a timed lock still applies.
     * Input: The saved lock time and the time of the check.
     * Output: True when the lock time is in the future.
     */
    public static boolean sharedCheckLockIsActive(Instant lockUntil, Instant sharedCheckLockIsActiveTime) {
        // An empty lock or one that has reached its end lets the user try again.
        return lockUntil != null && lockUntil.isAfter(sharedCheckLockIsActiveTime);
    }

    // ===== SECTION 4: sharedCheckTemporaryPasswordIsExpired =====

    /**
     * What it does: Checks whether a temporary password's expiry time has passed.
     * Input: The saved expiry time and the time of the check.
     * Output: True when the expiry time is in the past.
     */
    public static boolean sharedCheckTemporaryPasswordIsExpired(Instant temporaryPasswordExpiryTime,
                                                                 Instant sharedCheckTemporaryPasswordIsExpiredTime) {
        // Only a saved time in the past is expired.
        return temporaryPasswordExpiryTime != null
                && temporaryPasswordExpiryTime.isBefore(sharedCheckTemporaryPasswordIsExpiredTime);
    }
}
