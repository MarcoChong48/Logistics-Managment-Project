package com.lms.backend.admin.user;

// The Admin's reason for the account change.
public record AdminDeactivateUserRequest(
        String adminDeactivateUserReason) {
}
