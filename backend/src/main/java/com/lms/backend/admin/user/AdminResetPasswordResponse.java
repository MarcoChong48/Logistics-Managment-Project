package com.lms.backend.admin.user;

import java.time.Instant;

// The temporary password issued for the account and its expiry.
public record AdminResetPasswordResponse(
        Long userID,
        String username,
        String adminResetPasswordTemporaryPassword,
        Instant adminResetPasswordTemporaryPasswordExpiryTime) {
}
