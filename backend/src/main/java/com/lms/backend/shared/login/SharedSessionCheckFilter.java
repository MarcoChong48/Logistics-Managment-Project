package com.lms.backend.shared.login;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedSecurityEvent;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.value.SharedAccountStatus;

public class SharedSessionCheckFilter extends OncePerRequestFilter {

    // ===== SECTION 1: OTHER CLASSES THIS FILTER USES =====

    private final AppUserRepository sharedCheckSessionAppUserRepository;
    private final SharedSecurityLogWriter sharedCheckSessionLogWriter;
    private final HandlerExceptionResolver sharedCheckSessionErrorResolver;

    /**
     * What it does: Supplies the account repository, log writer and web error resolver to the filter.
     * Input: The three shared collaborators.
     * Output: A filter ready to validate existing sessions.
     */
    public SharedSessionCheckFilter(AppUserRepository sharedCheckSessionAppUserRepository,
            SharedSecurityLogWriter sharedCheckSessionLogWriter,
            HandlerExceptionResolver sharedCheckSessionErrorResolver) {
        this.sharedCheckSessionAppUserRepository = sharedCheckSessionAppUserRepository;
        this.sharedCheckSessionLogWriter = sharedCheckSessionLogWriter;
        this.sharedCheckSessionErrorResolver = sharedCheckSessionErrorResolver;
    }

    // ===== SECTION 2: sharedCheckSession =====

    /**
     * What it does: Runs the Stage 1 session check inside the security filter chain.
     * Input: The HTTP request, response and the remaining filters.
     * Output: The request moves on or receives a shared authorization response.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest sharedCheckSessionRequest,
            HttpServletResponse sharedCheckSessionResponse, FilterChain sharedCheckSessionChain)
            throws ServletException, IOException {
        // Keep database failures from escaping the application's shared JSON error handler.
        try {
            if (!sharedCheckSession(sharedCheckSessionRequest, sharedCheckSessionResponse)) {
                return;
            }
        } catch (RuntimeException sharedCheckSessionFailure) {
            sharedCheckSessionErrorResolver.resolveException(
                    sharedCheckSessionRequest, sharedCheckSessionResponse, null, sharedCheckSessionFailure);
            return;
        }
        sharedCheckSessionChain.doFilter(sharedCheckSessionRequest, sharedCheckSessionResponse);
    }

    /**
     * What it does: Validates the session against the latest account row.
     * Input: The HTTP request with its optional session and the response for a refusal.
     * Output: True to proceed, or false after returning an error or redirect.
     * Placeholder: PO-07 gives every role the same session revocation rules. Protect its paths with this filter.
     */
    private boolean sharedCheckSession(HttpServletRequest sharedCheckSessionRequest,
            HttpServletResponse sharedCheckSessionResponse) throws IOException {
        String sharedCheckSessionPath = sharedCheckSessionRequest.getServletPath();
        boolean sharedCheckSessionIsPublic =
                SharedSecurityConfiguration.sharedCheckOpenWebAddress(sharedCheckSessionRequest);
        // Session discovery is public, but it must still revoke an obsolete signed-in session.
        if (sharedCheckSessionIsPublic && !"/api/shared/session".equals(sharedCheckSessionPath)) {
            return true;
        }

        HttpSession sharedCheckSessionSession = sharedCheckSessionRequest.getSession(false);
        if (!(sharedReadSessionAttribute(sharedCheckSessionSession, "userID") instanceof Long userID)) {
            if (sharedCheckSessionIsPublic) {
                return true;
            }
            return sharedRejectSession(sharedCheckSessionRequest, sharedCheckSessionResponse,
                    HttpStatus.UNAUTHORIZED, "SHARED_NOT_LOGGED_IN", "Please log in.", "/shared/shared-login.html");
        }

        // PLACEHOLDER OUTPUT PO-07
        // To: Client, Warehouse and Delivery.
        // What: A changed role, status or sessionVersion revokes sessions that still have the old values.
        // How to use: Keep each role's protected routes inside this filter chain.
        AppUser sharedCheckSessionUser = sharedCheckSessionAppUserRepository.findById(userID).orElse(null);
        if (sharedCheckSessionUser == null || sharedCheckSessionUser.accountStatus != SharedAccountStatus.ACTIVE
                || sharedCheckSessionUser.role != sharedReadSessionAttribute(sharedCheckSessionSession, "role")
                || !Integer.valueOf(sharedCheckSessionUser.sessionVersion).equals(
                        sharedReadSessionAttribute(sharedCheckSessionSession, "sessionVersion"))) {
            if (sharedEndSession(sharedCheckSessionSession)) {
                sharedCheckSessionLogWriter.sharedWriteSecurityLog(sharedCheckSessionUser == null ? null : userID,
                        SharedSecurityEvent.SHARED_SECURITY_EVENT_SESSION_REVOKED, "SUCCESS", null);
            }
            return sharedRejectSession(sharedCheckSessionRequest, sharedCheckSessionResponse,
                    HttpStatus.UNAUTHORIZED, "SHARED_SESSION_ENDED", "Your session has ended. Please log in again.",
                    "/shared/shared-login.html?sharedLoginUserNotice=SESSION_ENDED");
        }

        // A temporary password session can only use the routes needed to replace it or sign out.
        if (sharedCheckSessionUser.mustChangePassword && !sharedCheckSessionIsPublic
                && !sharedCheckSessionPath.equals("/api/shared/session/extend")
                && !sharedCheckSessionPath.equals("/api/shared/change-password")) {
            return sharedRejectSession(sharedCheckSessionRequest, sharedCheckSessionResponse,
                    HttpStatus.FORBIDDEN, "SHARED_PASSWORD_CHANGE_REQUIRED", "Please change your password to continue.",
                    "/shared/shared-change-password.html");
        }

        // Spring checks the role after this filter confirms the session.
        var sharedCheckSessionCurrentUser =
                new SharedCurrentUser(userID, sharedCheckSessionUser.username, sharedCheckSessionUser.role);
        var sharedCheckSessionAuthority = new SimpleGrantedAuthority("ROLE_" + sharedCheckSessionUser.role.name());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                sharedCheckSessionCurrentUser, null, List.of(sharedCheckSessionAuthority)));
        sharedCheckSessionRequest.setAttribute("mustChangePassword", sharedCheckSessionUser.mustChangePassword);
        return true;
    }

    // ===== SECTION 3: HELPER FUNCTIONS =====

    /**
     * What it does: Reads a value while tolerating a concurrent logout.
     * Input: An optional session and an attribute name.
     * Output: The attribute, or null when the value or active session is absent.
     */
    static Object sharedReadSessionAttribute(HttpSession sharedReadSessionAttributeSession,
            String sharedReadSessionAttributeName) {
        if (sharedReadSessionAttributeSession == null) {
            return null;
        }
        try {
            return sharedReadSessionAttributeSession.getAttribute(sharedReadSessionAttributeName);
        } catch (IllegalStateException sharedReadSessionAttributeEnded) {
            return null;
        }
    }

    /**
     * What it does: Ends the given session once even if another request is ending it too.
     * Input: The session to end, if any.
     * Output: True if this request ended the session.
     */
    static boolean sharedEndSession(HttpSession sharedEndSessionSession) {
        if (sharedEndSessionSession == null) {
            return false;
        }
        try {
            sharedEndSessionSession.invalidate();
            return true;
        } catch (IllegalStateException sharedEndSessionAlreadyEnded) {
            return false;
        }
    }

    /**
     * What it does: Ends a refused request with shared JSON or a page redirect.
     * Input: The HTTP exchange, error status, code and message, and the page destination.
     * Output: False after sending the response.
     */
    private boolean sharedRejectSession(HttpServletRequest sharedRejectSessionRequest,
            HttpServletResponse sharedRejectSessionResponse, HttpStatus sharedRejectSessionStatus,
            String sharedRejectSessionCode, String sharedRejectSessionMessage, String sharedRejectSessionWebAddress)
            throws IOException {
        if (sharedRejectSessionRequest.getServletPath().startsWith("/api/")) {
            sharedCheckSessionErrorResolver.resolveException(sharedRejectSessionRequest, sharedRejectSessionResponse,
                    null, new SharedRuleException(sharedRejectSessionStatus, sharedRejectSessionCode,
                            sharedRejectSessionMessage));
        } else {
            sharedRejectSessionResponse.sendRedirect(sharedRejectSessionWebAddress);
        }
        return false;
    }
}
