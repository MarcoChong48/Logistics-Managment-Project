package com.lms.backend.shared.login;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.shared.error.SharedMessageResponse;

@RestController
public class SharedLoginController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final SharedLoginService sharedLoginService;

    /**
     * What it does: Receives the service for the five login and session routes.
     * Input: The shared login service.
     * Output: A controller ready to forward requests.
     */
    public SharedLoginController(SharedLoginService sharedLoginService) {
        this.sharedLoginService = sharedLoginService;
    }

    // ===== SECTION 2: POST /api/shared/login =====

    /**
     * What it does: Passes the entered credentials to the login service.
     * Input: The credentials record and the HTTP request.
     * Output: The login response, or a shared login error.
     */
    @PostMapping("/api/shared/login")
    public SharedLoginUserResponse sharedLoginUser(@RequestBody SharedLoginUserRequest sharedLoginUserRequest,
            HttpServletRequest sharedLoginUserHttpRequest) {
        return sharedLoginService.sharedLoginUser(sharedLoginUserRequest, sharedLoginUserHttpRequest);
    }

    // ===== SECTION 3: POST /api/shared/logout =====

    /**
     * What it does: Sends a logout request to the service.
     * Input: The HTTP request, which can carry a session.
     * Output: A logout message.
     */
    @PostMapping("/api/shared/logout")
    public SharedMessageResponse sharedLogoutUser(HttpServletRequest sharedLogoutUserHttpRequest) {
        return sharedLoginService.sharedLogoutUser(sharedLogoutUserHttpRequest);
    }

    // ===== SECTION 4: GET /api/shared/session =====

    /**
     * What it does: Asks the service for the current session and security token.
     * Input: The validated request and the token supplied by Spring Security.
     * Output: The session response for a guest or a signed-in user.
     */
    @GetMapping("/api/shared/session")
    public SharedViewSessionResponse sharedViewSession(HttpServletRequest sharedViewSessionHttpRequest,
            CsrfToken sharedViewSessionSecurityToken) {
        return sharedLoginService.sharedViewSession(sharedViewSessionHttpRequest, sharedViewSessionSecurityToken);
    }

    // ===== SECTION 5: POST /api/shared/session/extend =====

    /**
     * What it does: Confirms activity after security checked the session and token.
     * Input: None.
     * Output: A message that confirms the session extension.
     */
    @PostMapping("/api/shared/session/extend")
    public SharedMessageResponse sharedExtendSession() {
        return sharedLoginService.sharedExtendSession();
    }

    // ===== SECTION 6: POST /api/shared/change-password =====

    /**
     * What it does: Passes the entered passwords to the change-password service.
     * Input: The password form record and the HTTP request.
     * Output: A message asking the user to log in again, or a shared rule error.
     */
    @PostMapping("/api/shared/change-password")
    public SharedMessageResponse sharedChangePassword(
            @RequestBody SharedChangePasswordRequest sharedChangePasswordRequest,
            HttpServletRequest sharedChangePasswordHttpRequest) {
        return sharedLoginService.sharedChangePassword(sharedChangePasswordRequest, sharedChangePasswordHttpRequest);
    }
}
