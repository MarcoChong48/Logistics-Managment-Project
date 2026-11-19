package hk.edu.hkmu.lms.delivery.access;

import hk.edu.hkmu.lms.delivery.support.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class SessionController {
    private final DeliveryAccess service;
    private final AccountSecurity accountSecurity;

    SessionController(DeliveryAccess service, AccountSecurity accountSecurity) {
        this.service = service;
        this.accountSecurity = accountSecurity;
    }

    @GetMapping("/session")
    Map<String, Object> session(Authentication auth, CsrfToken csrf, HttpServletRequest request) {
        boolean signedIn = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());
        var timing = (SecurityConfig.SessionTiming) request.getAttribute(SecurityConfig.SESSION_TIMING_ATTRIBUTE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", signedIn);
        result.put("user", signedIn ? service.user(auth.getName()) : null);
        result.put("csrfToken", csrf.getToken());
        result.put("csrfHeader", csrf.getHeaderName());
        result.put("mode", service.mode());
        result.put("sessionExpiresAt", timing == null ? null : timing.expiresAt());
        result.put("serverTime", timing == null ? null : timing.serverTime());
        return result;
    }

    @PostMapping("/change-password")
    Map<String, String> changePassword(@RequestBody PasswordChange change, Authentication auth,
                                       HttpServletRequest request, HttpServletResponse response) {
        boolean changed = accountSecurity.changePassword(auth.getName(), change.currentPassword(),
            change.newPassword(), change.confirmPassword(), request);
        if (!changed) {
            throw ApiException.bad("Your current password is incorrect.");
        }
        SecurityConfig.logoutSession(request, response, auth);
        return Map.of("message", "Password changed. Sign in again.");
    }

    record PasswordChange(String currentPassword, String newPassword, String confirmPassword) {
        @Override
        public String toString() {
            return "[PROTECTED]";
        }
    }
}
