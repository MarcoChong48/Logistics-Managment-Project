package com.lms.backend.admin.user;

import java.time.Instant;

import com.lms.backend.shared.value.SharedUserRole;

// The account identity and the temporary password just issued.
public record AdminCreateUserResponse(
        Long userID,
        String username,
        SharedUserRole role,
        String adminCreateUserTemporaryPassword,
        Instant adminCreateUserTemporaryPasswordExpiryTime) {
}
