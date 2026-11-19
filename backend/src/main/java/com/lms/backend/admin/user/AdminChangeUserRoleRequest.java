package com.lms.backend.admin.user;

// The selected staff role and the Admin's reason for the change.
public record AdminChangeUserRoleRequest(
        String adminChangeUserRoleNewRole,
        String adminChangeUserRoleReason) {
}
