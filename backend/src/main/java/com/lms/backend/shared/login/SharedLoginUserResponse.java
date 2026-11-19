package com.lms.backend.shared.login;

import com.lms.backend.shared.value.SharedUserRole;

/** The signed-in user's basic details and the page the browser should open. */
public record SharedLoginUserResponse(Long userID, String username, SharedUserRole role,
        boolean mustChangePassword, String sharedLoginUserNextPageWebAddress) {
}
