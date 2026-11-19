package com.lms.backend.shared.database;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

// Shared database placeholder. Use the team's repository once it provides these account queries.
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // ===== SECTION 1: ACCOUNT LOOKUPS =====

    /**
     * What it does: Reads an account using the same case-insensitive comparison as the unique index.
     * Input: The username to match.
     * Output: The matching user, or an empty result.
     */
    @Query("SELECT account FROM AppUser account WHERE LOWER(account.username) = LOWER(:username)")
    Optional<AppUser> findByUsernameIgnoreCase(@Param("username") String username);

    /**
     * What it does: Reads an account and locks changes to it until the caller's transaction ends.
     * Input: The username to match, ignoring case.
     * Output: The matching user, or an empty result.
     */
    @Query(value = "SELECT * FROM app_user WHERE LOWER(username) = LOWER(:username) FOR NO KEY UPDATE",
            nativeQuery = true)
    Optional<AppUser> findByUsernameForChange(@Param("username") String username);

    /**
     * What it does: Locks the account before a credential change while permitting foreign-key reads.
     * Input: The account ID.
     * Output: The matching user, or an empty result.
     */
    @Query(value = "SELECT * FROM app_user WHERE user_id = :userID FOR NO KEY UPDATE", nativeQuery = true)
    Optional<AppUser> findByUserIDForChange(@Param("userID") Long userID);

    // ===== SECTION 2: BOOTSTRAP LOOKUP =====

    /**
     * What it does: Checks if an account in the supplied role and state can still sign in.
     * Input: The required role and state, and the time of the bootstrap check.
     * Output: True when a matching account has a permanent or unexpired temporary password.
     */
    @Query("""
            SELECT CASE WHEN COUNT(account) > 0 THEN true ELSE false END
            FROM AppUser account
            WHERE account.role = :role AND account.accountStatus = :accountStatus
              AND (account.mustChangePassword = false OR account.temporaryPasswordExpiryTime IS NULL
                   OR account.temporaryPasswordExpiryTime >= :sharedCreateBootstrapAdminTime)
            """)
    boolean sharedCreateBootstrapAdminHasUsableAccount(
            @Param("role") SharedUserRole role,
            @Param("accountStatus") SharedAccountStatus accountStatus,
            @Param("sharedCreateBootstrapAdminTime") Instant sharedCreateBootstrapAdminTime);

    /**
     * What it does: Makes concurrent starts finish the Admin bootstrap check one at a time.
     * Input: None. The caller must already be in a transaction.
     * Output: A fixed row once this transaction owns the bootstrap lock.
     */
    @Query(value = """
            SELECT 1
            FROM pg_advisory_xact_lock(hashtextextended('com.lms.backend.shared.login.SharedBootstrapAdminRunner', 0))
            """, nativeQuery = true)
    int sharedCreateBootstrapAdminLock();

    // ===== SECTION 3: ADMIN ACCOUNT SEARCH =====

    /**
     * What it does: Finds a page of accounts by literal username text, role or status.
     * Input: The optional filters, the lock check time and the requested page.
     * Output: Matching accounts in username order and the total row count.
     */
    @Query("""
            SELECT account FROM AppUser account
            WHERE LOCATE(LOWER(:adminSearchUserKeyword), LOWER(account.username)) > 0
              AND (:role IS NULL OR account.role = :role)
              AND (:accountStatus IS NULL OR account.accountStatus = :accountStatus)
              AND (:adminSearchUserIsLockedOnly = false OR account.accountStatus = :adminSearchUserOldLockStatus
                   OR account.lockUntil > :adminSearchUserTime)
            ORDER BY account.username
            """)
    Page<AppUser> adminSearchUser(
            @Param("adminSearchUserKeyword") String adminSearchUserKeyword,
            @Param("role") SharedUserRole role,
            @Param("accountStatus") SharedAccountStatus accountStatus,
            @Param("adminSearchUserIsLockedOnly") boolean adminSearchUserIsLockedOnly,
            @Param("adminSearchUserOldLockStatus") SharedAccountStatus adminSearchUserOldLockStatus,
            @Param("adminSearchUserTime") Instant adminSearchUserTime,
            Pageable adminSearchUserPage);

    // ===== SECTION 4: ADMIN ACCOUNT CHANGES =====

    /**
     * What it does: Reads the current role without loading an account entity.
     * Input: The target user ID.
     * Output: The observed role, or an empty result when the account is missing.
     */
    @Query("SELECT account.role FROM AppUser account WHERE account.userID = :userID")
    Optional<SharedUserRole> findRoleByUserID(@Param("userID") Long userID);

    /**
     * What it does: Locks all Admin accounts in the same order before a change could remove an active Admin.
     * Input: None. The caller must hold no account locks yet.
     * Output: The Admin accounts seen after waiting for other changes.
     */
    @Query(value = "SELECT * FROM app_user WHERE role = 'ADMIN' ORDER BY user_id FOR NO KEY UPDATE",
            nativeQuery = true)
    List<AppUser> findAdminAccountsForChange();

    /**
     * What it does: Changes the role and session version only while the expected role and status still hold.
     * Input: The target ID, old and new roles, required status and change time.
     * Output: The number of account rows changed.
     */
    @Modifying
    @Query("""
            UPDATE AppUser account
            SET account.role = :adminChangeUserRoleNewRole,
                account.sessionVersion = account.sessionVersion + 1,
                account.updatedTime = :updatedTime
            WHERE account.userID = :userID AND account.role = :role AND account.accountStatus = :accountStatus
            """)
    int adminChangeUserRole(
            @Param("userID") Long userID,
            @Param("role") SharedUserRole role,
            @Param("adminChangeUserRoleNewRole") SharedUserRole adminChangeUserRoleNewRole,
            @Param("accountStatus") SharedAccountStatus accountStatus,
            @Param("updatedTime") Instant updatedTime);
}
