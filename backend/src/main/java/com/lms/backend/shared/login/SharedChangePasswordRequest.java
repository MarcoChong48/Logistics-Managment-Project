package com.lms.backend.shared.login;

/** The three exact password values entered on the change-password page. */
public record SharedChangePasswordRequest(String sharedChangePasswordCurrentPassword,
        String sharedChangePasswordNewPassword, String sharedChangePasswordConfirmPassword) {
}
