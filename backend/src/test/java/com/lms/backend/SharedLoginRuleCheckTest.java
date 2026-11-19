package com.lms.backend;

import static com.lms.backend.shared.login.SharedLoginRule.sharedCheckLockIsActive;
import static com.lms.backend.shared.login.SharedLoginRule.sharedCheckTemporaryPasswordIsExpired;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SharedLoginRuleCheckTest {

    // ===== SECTION 1: ACCOUNT LOCK CHECKS =====

    /**
     * What it does: Checks that a future lock is still active.
     * Input: A lock end one second after the check.
     * Output: Pass when the lock is active.
     */
    @Test
    void sharedCheckLockIsActiveForFutureTime() {
        Instant sharedCheckLockIsActiveTime = Instant.EPOCH;
        assertTrue(sharedCheckLockIsActive(sharedCheckLockIsActiveTime.plusSeconds(1), sharedCheckLockIsActiveTime));
    }

    /**
     * What it does: Checks that a lock in the past has ended.
     * Input: A lock end one second before the check.
     * Output: Pass when the lock is inactive.
     */
    @Test
    void sharedCheckLockIsActiveIsFalseForPastTime() {
        Instant sharedCheckLockIsActiveTime = Instant.EPOCH;
        assertFalse(sharedCheckLockIsActive(sharedCheckLockIsActiveTime.minusSeconds(1), sharedCheckLockIsActiveTime));
    }

    /**
     * What it does: Checks that an empty timestamp gives no lock.
     * Input: No saved lock time.
     * Output: Pass when the lock is inactive.
     */
    @Test
    void sharedCheckLockIsActiveIsFalseForEmptyTime() {
        assertFalse(sharedCheckLockIsActive(null, Instant.EPOCH));
    }

    /**
     * What it does: Checks the time at which an account lock stops applying.
     * Input: Matching lock end and check times.
     * Output: Pass when the lock is inactive.
     */
    @Test
    void sharedCheckLockIsActiveIsFalseAtEnd() {
        assertFalse(sharedCheckLockIsActive(Instant.EPOCH, Instant.EPOCH));
    }

    // ===== SECTION 2: TEMPORARY PASSWORD EXPIRY CHECKS =====

    /**
     * What it does: Checks whether a past temporary password expiry is expired.
     * Input: An expiry one second before the check.
     * Output: Pass when the password is expired.
     */
    @Test
    void sharedCheckTemporaryPasswordIsExpiredForPastTime() {
        Instant sharedCheckTemporaryPasswordIsExpiredTime = Instant.EPOCH;
        assertTrue(sharedCheckTemporaryPasswordIsExpired(
                sharedCheckTemporaryPasswordIsExpiredTime.minusSeconds(1), sharedCheckTemporaryPasswordIsExpiredTime));
    }

    /**
     * What it does: Checks a temporary password with an expiry time still ahead.
     * Input: An expiry one second after the check.
     * Output: Pass when the password is unexpired.
     */
    @Test
    void sharedCheckTemporaryPasswordIsExpiredIsFalseForFutureTime() {
        Instant sharedCheckTemporaryPasswordIsExpiredTime = Instant.EPOCH;
        assertFalse(sharedCheckTemporaryPasswordIsExpired(
                sharedCheckTemporaryPasswordIsExpiredTime.plusSeconds(1), sharedCheckTemporaryPasswordIsExpiredTime));
    }

    /**
     * What it does: Checks the instant at which the temporary password expiry is reached.
     * Input: Matching expiry and check times.
     * Output: Pass when the expiry is not yet in the past.
     */
    @Test
    void sharedCheckTemporaryPasswordIsExpiredIsFalseAtExpiry() {
        assertFalse(sharedCheckTemporaryPasswordIsExpired(Instant.EPOCH, Instant.EPOCH));
    }

    /**
     * What it does: Checks an account with no saved expiry date.
     * Input: An empty expiry time.
     * Output: Pass when there is no past expiry.
     */
    @Test
    void sharedCheckTemporaryPasswordIsExpiredIsFalseForEmptyTime() {
        assertFalse(sharedCheckTemporaryPasswordIsExpired(null, Instant.EPOCH));
    }
}
