package com.lms.backend.shared.login;

import com.lms.backend.shared.value.SharedUserRole;

/** The signed-in user's identity for the current request. */
public record SharedCurrentUser(Long userID, String username, SharedUserRole role) {
}
