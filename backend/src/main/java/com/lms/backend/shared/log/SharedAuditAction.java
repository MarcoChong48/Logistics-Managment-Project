package com.lms.backend.shared.log;

public final class SharedAuditAction {

    // ===== SECTION 1: USER ACCOUNT ACTIONS =====

    public static final String SHARED_AUDIT_ACTION_USER_CREATED = "USER_CREATED";
    public static final String SHARED_AUDIT_ACTION_USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    public static final String SHARED_AUDIT_ACTION_USER_BANNED = "USER_BANNED";
    public static final String SHARED_AUDIT_ACTION_USER_UNBANNED = "USER_UNBANNED";
    public static final String SHARED_AUDIT_ACTION_USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String SHARED_AUDIT_ACTION_USER_REACTIVATED = "USER_REACTIVATED";
    public static final String SHARED_AUDIT_ACTION_USER_UNLOCKED = "USER_UNLOCKED";
    public static final String SHARED_AUDIT_ACTION_USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String SHARED_AUDIT_ACTION_DELIVERY_WORKER_PROFILE_EDITED = "DELIVERY_WORKER_PROFILE_EDITED";

    // ===== SECTION 2: LOG ACTIONS =====

    public static final String SHARED_AUDIT_ACTION_LOG_CLEANUP_RUN = "LOG_CLEANUP_RUN";
    public static final String SHARED_AUDIT_ACTION_CSV_DOWNLOADED = "CSV_DOWNLOADED";

    // ===== SECTION 3: CONSTRUCTOR =====

    /**
     * What it does: Keeps the fixed action words from being created as an object.
     * Input: None.
     * Output: None.
     */
    private SharedAuditAction() {
    }
}
