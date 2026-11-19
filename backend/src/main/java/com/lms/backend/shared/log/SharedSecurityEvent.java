package com.lms.backend.shared.log;

public final class SharedSecurityEvent {

    // ===== SECTION 1: LOGIN EVENT WORDS =====

    public static final String SHARED_SECURITY_EVENT_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String SHARED_SECURITY_EVENT_LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String SHARED_SECURITY_EVENT_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String SHARED_SECURITY_EVENT_LOGOUT = "LOGOUT";
    public static final String SHARED_SECURITY_EVENT_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String SHARED_SECURITY_EVENT_TEMPORARY_PASSWORD_ISSUED = "TEMPORARY_PASSWORD_ISSUED";
    public static final String SHARED_SECURITY_EVENT_SESSION_REVOKED = "SESSION_REVOKED";
    public static final String SHARED_SECURITY_EVENT_ACCESS_DENIED = "ACCESS_DENIED";
    public static final String SHARED_SECURITY_EVENT_ACCOUNT_UNLOCKED_BY_ADMIN = "ACCOUNT_UNLOCKED_BY_ADMIN";
    public static final String SHARED_SECURITY_EVENT_ACCOUNT_BANNED = "ACCOUNT_BANNED";
    public static final String SHARED_SECURITY_EVENT_ACCOUNT_UNBANNED = "ACCOUNT_UNBANNED";
    public static final String SHARED_SECURITY_EVENT_ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";
    public static final String SHARED_SECURITY_EVENT_ACCOUNT_REACTIVATED = "ACCOUNT_REACTIVATED";
    public static final String SHARED_SECURITY_EVENT_ROLE_CHANGED = "ROLE_CHANGED";
    public static final String SHARED_SECURITY_EVENT_REJECTED_ADMIN_ACTION = "REJECTED_ADMIN_ACTION";

    // ===== SECTION 2: CONSTRUCTOR =====

    /**
     * What it does: Keeps the event words from being created as an object.
     * Input: None.
     * Output: None.
     */
    private SharedSecurityEvent() {
    }
}
