package com.lms.backend.shared.login;

import com.lms.backend.shared.value.SharedUserRole;

/** The current session, the header required for changing requests, and test mode. */
public record SharedViewSessionResponse(boolean sharedViewSessionIsLoggedIn,
        Long userID, String username, SharedUserRole role, Boolean mustChangePassword,
        String sharedViewSessionSecurityToken, String sharedViewSessionSecurityTokenHeaderName,
        int sharedViewSessionIdleTimeoutSeconds, boolean sharedViewSessionTestModeIsEnabled,
        String sharedViewSessionNextPageWebAddress) {
}
