package com.lms.backend.shared.login;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.lms.backend.shared.error.SharedRuleException;

@Component
public class SharedCurrentUserReader {

    // ===== SECTION 1: sharedReadCurrentUser =====

    /**
     * What it does: Reads the identity validated against the session for this request.
     * Input: None.
     * Output: The current user, or 401 SHARED_NOT_LOGGED_IN.
     */
    public SharedCurrentUser sharedReadCurrentUser() {
        // Authentication for this request carries the identity from the checked session.
        Authentication sharedReadCurrentUserAuthentication = SecurityContextHolder.getContext().getAuthentication();
        if (sharedReadCurrentUserAuthentication != null
                && sharedReadCurrentUserAuthentication.getPrincipal()
                        instanceof SharedCurrentUser sharedReadCurrentUser) {
            return sharedReadCurrentUser;
        }
        throw new SharedRuleException(HttpStatus.UNAUTHORIZED, "SHARED_NOT_LOGGED_IN", "Please log in.");
    }
}
