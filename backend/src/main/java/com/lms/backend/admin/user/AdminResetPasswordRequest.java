package com.lms.backend.admin.user;

// Identity confirmation and its recorded method.
public record AdminResetPasswordRequest(
        boolean adminResetPasswordIdentityIsConfirmed,
        String adminResetPasswordIdentityCheckMethod) {
}
