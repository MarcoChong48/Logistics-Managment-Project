package com.lms.backend.shared.login;

import static com.lms.backend.shared.login.SharedLoginRule.SHARED_LOGIN_LOCK_MINUTES;
import static com.lms.backend.shared.login.SharedLoginRule.SHARED_LOGIN_MAXIMUM_FAILED_ATTEMPTS;
import static com.lms.backend.shared.login.SharedLoginRule.SHARED_PASSWORD_MAXIMUM_UTF8_BYTE_COUNT;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.error.SharedMessageResponse;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedSecurityEvent;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

@Service
public class SharedLoginService {

    // ===== SECTION 1: CONSTANTS =====

    // Login limits come from SharedLoginRule. The servlet timeout comes from application.properties.

    // ===== SECTION 2: OTHER CLASSES THIS CLASS USES =====

    private final AppUserRepository sharedLoginUserRepository;
    private final SharedSecurityLogWriter sharedLoginUserLogWriter;
    private final PasswordEncoder sharedLoginUserPasswordEncoder;
    private final SharedCurrentUserReader sharedChangePasswordCurrentUserReader;
    private final TransactionTemplate sharedLoginUserTransaction;
    private final Environment sharedViewSessionEnvironment;
    private final int sharedViewSessionIdleTimeoutSeconds;

    /**
     * What it does: Supplies the login service with its account, security, and session settings.
     * Input: The account repository, log writer, password encoder, session reader, transactions, and settings.
     * Output: A login service using the configured idle timeout.
     */
    public SharedLoginService(AppUserRepository sharedLoginUserRepository,
            SharedSecurityLogWriter sharedLoginUserLogWriter, PasswordEncoder sharedLoginUserPasswordEncoder,
            SharedCurrentUserReader sharedChangePasswordCurrentUserReader,
            PlatformTransactionManager sharedLoginUserTransactionManager, Environment sharedViewSessionEnvironment,
            @Value("${lms.shared.session-idle-timeout-minutes}") int sharedViewSessionIdleTimeoutMinutes) {
        this.sharedLoginUserRepository = sharedLoginUserRepository;
        this.sharedLoginUserLogWriter = sharedLoginUserLogWriter;
        this.sharedLoginUserPasswordEncoder = sharedLoginUserPasswordEncoder;
        this.sharedChangePasswordCurrentUserReader = sharedChangePasswordCurrentUserReader;
        this.sharedLoginUserTransaction = new TransactionTemplate(sharedLoginUserTransactionManager);
        this.sharedViewSessionEnvironment = sharedViewSessionEnvironment;
        this.sharedViewSessionIdleTimeoutSeconds = Math.multiplyExact(sharedViewSessionIdleTimeoutMinutes, 60);
    }

    // ===== SECTION 3: sharedLoginUser =====

    /**
     * What it does: Checks credentials and starts a fresh session on success.
     * Input: The entered username and password and the servlet request.
     * Output: The signed-in user and next page, or the Stage 1 login error.
     * Placeholder: PO-10 is inside sharedFindStartPage. The other teams set their start pages there.
     */
    public SharedLoginUserResponse sharedLoginUser(SharedLoginUserRequest sharedLoginUserRequest,
            HttpServletRequest sharedLoginUserHttpRequest) {
        // Require both fields. Preserve the original password for the hash check.
        String sharedLoginUserUsername = sharedLoginUserRequest.sharedLoginUserUsername();
        String sharedLoginUserPassword = sharedLoginUserRequest.sharedLoginUserPassword();
        if (sharedLoginUserUsername == null || sharedLoginUserUsername.isBlank()
                || sharedLoginUserPassword == null || sharedLoginUserPassword.isEmpty()) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID",
                    "Enter your username and password.");
        }

        // Commit the account update before writing the independent event or starting a session.
        Supplier<SharedLoginUserResponse> sharedLoginUserOutcome = sharedLoginUserTransaction.execute(
                sharedLoginUserTransactionStatus -> sharedCheckLoginAccount(
                        sharedLoginUserUsername, sharedLoginUserPassword, sharedLoginUserHttpRequest));
        return sharedLoginUserOutcome.get();
    }

    // ===== SECTION 4: sharedLogoutUser =====

    /**
     * What it does: Ends the servlet session and records logout for a known signed-in user.
     * Input: The servlet request with its optional session.
     * Output: A logout message, including for a guest.
     */
    public SharedMessageResponse sharedLogoutUser(HttpServletRequest sharedLogoutUserHttpRequest) {
        // Discard the token and credentials in the same operation.
        HttpSession sharedLogoutUserSession = sharedLogoutUserHttpRequest.getSession(false);
        Object sharedLogoutUserSessionUserID =
                SharedSessionCheckFilter.sharedReadSessionAttribute(sharedLogoutUserSession, "userID");
        if (SharedSessionCheckFilter.sharedEndSession(sharedLogoutUserSession)
                && sharedLogoutUserSessionUserID instanceof Long userID
                && sharedLoginUserRepository.existsById(userID)) {
            sharedLoginUserLogWriter.sharedWriteSecurityLog(
                    userID, SharedSecurityEvent.SHARED_SECURITY_EVENT_LOGOUT, "SUCCESS", null);
        }
        return new SharedMessageResponse("You are logged out.");
    }

    // ===== SECTION 5: sharedViewSession =====

    /**
     * What it does: Reports the checked session and supplies the token for subsequent requests.
     * Input: The servlet request and Spring Security's token.
     * Output: A guest or signed-in session response.
     * Placeholder: PO-10 is inside sharedFindStartPage. All roles choose their start page there.
     */
    public SharedViewSessionResponse sharedViewSession(HttpServletRequest sharedViewSessionHttpRequest,
            CsrfToken sharedViewSessionSecurityToken) {
        // The session filter has already checked a signed-in user's credentials against the account.
        Authentication sharedViewSessionAuthentication = SecurityContextHolder.getContext().getAuthentication();
        boolean sharedViewSessionTestModeIsEnabled = sharedViewSessionEnvironment.matchesProfiles("admintest");
        if (sharedViewSessionAuthentication != null
                && sharedViewSessionAuthentication.getPrincipal() instanceof SharedCurrentUser sharedViewSessionUser) {
            boolean mustChangePassword =
                    Boolean.TRUE.equals(sharedViewSessionHttpRequest.getAttribute("mustChangePassword"));
            return new SharedViewSessionResponse(true, sharedViewSessionUser.userID(), sharedViewSessionUser.username(),
                    sharedViewSessionUser.role(), mustChangePassword,
                    sharedViewSessionSecurityToken.getToken(), sharedViewSessionSecurityToken.getHeaderName(),
                    sharedViewSessionIdleTimeoutSeconds, sharedViewSessionTestModeIsEnabled,
                    sharedResolveStartPage(mustChangePassword, sharedViewSessionUser.role()));
        }

        // A guest's token allows a protected form submission without authenticating them.
        return new SharedViewSessionResponse(false, null, null, null, null,
                sharedViewSessionSecurityToken.getToken(), sharedViewSessionSecurityToken.getHeaderName(),
                sharedViewSessionIdleTimeoutSeconds, sharedViewSessionTestModeIsEnabled, null);
    }

    // ===== SECTION 6: sharedExtendSession =====

    /**
     * What it does: Confirms the activity recorded for this request by the servlet session.
     * Input: None.
     * Output: A message confirming the extension.
     */
    public SharedMessageResponse sharedExtendSession() {
        return new SharedMessageResponse("Session extended.");
    }

    // ===== SECTION 7: sharedChangePassword =====

    /**
     * What it does: Changes the password and revokes all sessions issued with the old password.
     * Input: The current, new and confirmation passwords and the servlet request.
     * Output: A message asking the user to sign in again, or an error for the field the user must correct.
     */
    public SharedMessageResponse sharedChangePassword(SharedChangePasswordRequest sharedChangePasswordRequest,
            HttpServletRequest sharedChangePasswordHttpRequest) {
        // Recheck the same session after locking the account row, in case another request changed it.
        SharedCurrentUser sharedChangePasswordUser = sharedChangePasswordCurrentUserReader.sharedReadCurrentUser();
        HttpSession sharedChangePasswordSession = sharedChangePasswordHttpRequest.getSession(false);
        Object sharedChangePasswordSessionVersion =
                SharedSessionCheckFilter.sharedReadSessionAttribute(sharedChangePasswordSession, "sessionVersion");
        if (sharedChangePasswordSessionVersion == null) {
            throw new SharedRuleException(HttpStatus.UNAUTHORIZED, "SHARED_SESSION_ENDED",
                    "Your session has ended. Please log in again.");
        }
        Boolean sharedChangePasswordIsSaved = sharedLoginUserTransaction.execute(
                sharedChangePasswordTransactionStatus -> {
            AppUser sharedChangePasswordAccount = sharedLoginUserRepository
                    .findByUserIDForChange(sharedChangePasswordUser.userID()).orElse(null);
            if (sharedChangePasswordAccount == null
                    || sharedChangePasswordAccount.accountStatus != SharedAccountStatus.ACTIVE
                    || sharedChangePasswordAccount.role != sharedChangePasswordUser.role()
                    || !Integer.valueOf(sharedChangePasswordAccount.sessionVersion).equals(
                            sharedChangePasswordSessionVersion)) {
                return false;
            }

            sharedSavePassword(sharedChangePasswordRequest, sharedChangePasswordAccount);
            return true;
        });

        // End this session after commit. The new version also ends other sessions on their next request.
        boolean sharedChangePasswordSessionWasEnded =
                SharedSessionCheckFilter.sharedEndSession(sharedChangePasswordSession);
        Long userID = sharedChangePasswordUser.userID();
        if (!Boolean.TRUE.equals(sharedChangePasswordIsSaved)) {
            if (sharedChangePasswordSessionWasEnded) {
                sharedLoginUserLogWriter.sharedWriteSecurityLog(
                        sharedLoginUserRepository.existsById(userID) ? userID : null,
                        SharedSecurityEvent.SHARED_SECURITY_EVENT_SESSION_REVOKED, "SUCCESS", null);
            }
            throw new SharedRuleException(HttpStatus.UNAUTHORIZED, "SHARED_SESSION_ENDED",
                    "Your session has ended. Please log in again.");
        }
        sharedLoginUserLogWriter.sharedWriteSecurityLog(
                userID, SharedSecurityEvent.SHARED_SECURITY_EVENT_PASSWORD_CHANGED, "SUCCESS", null);
        return new SharedMessageResponse("Password changed. Please log in again.");
    }

    // ===== SECTION 8: HELPER FUNCTIONS =====

    /**
     * What it does: Checks the account under a row lock and updates its failed-login state.
     * Input: The supplied credentials and the servlet request.
     * Output: The result to complete after the account transaction commits.
     */
    private Supplier<SharedLoginUserResponse> sharedCheckLoginAccount(String sharedCheckLoginAccountUsername,
            String sharedCheckLoginAccountPassword, HttpServletRequest sharedCheckLoginAccountHttpRequest) {
        AppUser sharedCheckLoginAccountUser = sharedLoginUserRepository
                .findByUsernameForChange(sharedCheckLoginAccountUsername).orElse(null);
        if (sharedCheckLoginAccountUser == null) {
            return sharedRefuseLogin(null, SharedSecurityEvent.SHARED_SECURITY_EVENT_LOGIN_FAILURE, "unknown username",
                    new SharedRuleException(HttpStatus.UNAUTHORIZED, "SHARED_LOGIN_FAILED",
                            "Wrong username or password."));
        }

        // Do not check a password or add to the count while a lock is in effect.
        Long userID = sharedCheckLoginAccountUser.userID;
        Instant sharedCheckLoginAccountTime = Instant.now();
        if (SharedLoginRule.sharedCheckLockIsActive(
                sharedCheckLoginAccountUser.lockUntil, sharedCheckLoginAccountTime)) {
            return sharedRefuseLogin(userID, SharedSecurityEvent.SHARED_SECURITY_EVENT_LOGIN_FAILURE, "account locked",
                    sharedExplainAccountLock(sharedCheckLoginAccountUser.lockUntil, sharedCheckLoginAccountTime));
        }
        if (sharedCheckLoginAccountUser.lockUntil != null) {
            sharedCheckLoginAccountUser.failedLoginCount = 0;
            sharedCheckLoginAccountUser.lockUntil = null;
        }

        if (!sharedMatchPassword(sharedCheckLoginAccountPassword, sharedCheckLoginAccountUser.passwordHash)) {
            return sharedCountLoginFailure(sharedCheckLoginAccountUser, sharedCheckLoginAccountTime);
        }
        if (sharedCheckLoginAccountUser.accountStatus != SharedAccountStatus.ACTIVE) {
            return sharedRefuseLogin(userID, SharedSecurityEvent.SHARED_SECURITY_EVENT_LOGIN_FAILURE,
                    "account not active",
                    new SharedRuleException(HttpStatus.FORBIDDEN, "SHARED_ACCOUNT_NOT_ACTIVE",
                            "This account cannot be used. Please contact an Admin."));
        }
        if (sharedCheckLoginAccountUser.mustChangePassword && SharedLoginRule.sharedCheckTemporaryPasswordIsExpired(
                sharedCheckLoginAccountUser.temporaryPasswordExpiryTime, sharedCheckLoginAccountTime)) {
            return sharedRefuseLogin(userID, SharedSecurityEvent.SHARED_SECURITY_EVENT_LOGIN_FAILURE,
                    "temporary password expired",
                    new SharedRuleException(HttpStatus.UNAUTHORIZED, "SHARED_TEMPORARY_PASSWORD_EXPIRED",
                            "This temporary password has expired. Please ask an Admin for a new one."));
        }

        // Sign-in clears failed attempts. The session is created only after this update commits.
        if (sharedCheckLoginAccountUser.failedLoginCount != 0) {
            sharedCheckLoginAccountUser.failedLoginCount = 0;
        }
        return () -> sharedStartLoginSession(sharedCheckLoginAccountUser, sharedCheckLoginAccountHttpRequest);
    }

    /**
     * What it does: Counts an active user's wrong password and applies the fifth-attempt lock.
     * Input: The locked account row and time of the attempt.
     * Output: The failure event and error to complete after commit.
     */
    private Supplier<SharedLoginUserResponse> sharedCountLoginFailure(AppUser sharedCountLoginFailureUser,
            Instant sharedCountLoginFailureTime) {
        if (sharedCountLoginFailureUser.accountStatus == SharedAccountStatus.ACTIVE) {
            sharedCountLoginFailureUser.failedLoginCount++;
            if (sharedCountLoginFailureUser.failedLoginCount >= SHARED_LOGIN_MAXIMUM_FAILED_ATTEMPTS) {
                sharedCountLoginFailureUser.lockUntil = sharedCountLoginFailureTime.plus(
                        Duration.ofMinutes(SHARED_LOGIN_LOCK_MINUTES));
                return sharedRefuseLogin(sharedCountLoginFailureUser.userID,
                        SharedSecurityEvent.SHARED_SECURITY_EVENT_ACCOUNT_LOCKED, null,
                        sharedExplainAccountLock(sharedCountLoginFailureUser.lockUntil, sharedCountLoginFailureTime));
            }
        }

        return sharedRefuseLogin(sharedCountLoginFailureUser.userID,
                SharedSecurityEvent.SHARED_SECURITY_EVENT_LOGIN_FAILURE, "wrong password",
                new SharedRuleException(HttpStatus.UNAUTHORIZED, "SHARED_LOGIN_FAILED", "Wrong username or password."));
    }

    /**
     * What it does: Prepares a sign-in refusal without rolling back a failed-attempt counter.
     * Input: The optional user ID, event, safe details, and public rule error.
     * Output: An action that logs the refusal and throws the error after commit.
     */
    private Supplier<SharedLoginUserResponse> sharedRefuseLogin(Long userID, String sharedRefuseLoginEventType,
            String sharedRefuseLoginDetails, SharedRuleException sharedRefuseLoginError) {
        return () -> {
            sharedLoginUserLogWriter.sharedWriteSecurityLog(
                    userID, sharedRefuseLoginEventType, "FAILURE", sharedRefuseLoginDetails);
            throw sharedRefuseLoginError;
        };
    }

    /**
     * What it does: Describes the time left on an active lock using whole minutes rounded up.
     * Input: The lock end time and the time of this request.
     * Output: The account-locked error.
     */
    private SharedRuleException sharedExplainAccountLock(Instant lockUntil, Instant sharedExplainAccountLockTime) {
        long sharedExplainAccountLockMinutes = Math.max(1,
                (Duration.between(sharedExplainAccountLockTime, lockUntil).toMillis() + 59999) / 60000);
        return new SharedRuleException(HttpStatus.UNAUTHORIZED, "SHARED_ACCOUNT_LOCKED",
                "This account is locked after too many wrong passwords. Try again in "
                        + sharedExplainAccountLockMinutes + " minutes.");
    }

    /**
     * What it does: Starts a new authenticated session and selects the first page.
     * Input: The account accepted by login and the servlet request.
     * Output: The login response for the new session.
     */
    private SharedLoginUserResponse sharedStartLoginSession(AppUser sharedStartLoginSessionUser,
            HttpServletRequest sharedStartLoginSessionHttpRequest) {
        // Record the success without including any credential.
        sharedLoginUserLogWriter.sharedWriteSecurityLog(
                sharedStartLoginSessionUser.userID, SharedSecurityEvent.SHARED_SECURITY_EVENT_LOGIN_SUCCESS,
                "SUCCESS", null);

        // A fresh session discards the pre-login session ID and token.
        SharedSessionCheckFilter.sharedEndSession(sharedStartLoginSessionHttpRequest.getSession(false));
        HttpSession sharedStartLoginSessionNewSession = sharedStartLoginSessionHttpRequest.getSession(true);
        sharedStartLoginSessionNewSession.setMaxInactiveInterval(sharedViewSessionIdleTimeoutSeconds);
        sharedStartLoginSessionNewSession.setAttribute("userID", sharedStartLoginSessionUser.userID);
        sharedStartLoginSessionNewSession.setAttribute("role", sharedStartLoginSessionUser.role);
        sharedStartLoginSessionNewSession.setAttribute("sessionVersion", sharedStartLoginSessionUser.sessionVersion);

        String sharedLoginUserNextPageWebAddress = sharedResolveStartPage(
                sharedStartLoginSessionUser.mustChangePassword, sharedStartLoginSessionUser.role);
        return new SharedLoginUserResponse(sharedStartLoginSessionUser.userID, sharedStartLoginSessionUser.username,
                sharedStartLoginSessionUser.role, sharedStartLoginSessionUser.mustChangePassword,
                sharedLoginUserNextPageWebAddress);
    }

    /**
     * What it does: Chooses the first page the current account can open.
     * Input: The account's password-change flag and role.
     * Output: The password-change page or the role's start page.
     * Placeholder: PO-10 is inside sharedFindStartPage. Other owners configure their pages there.
     */
    private String sharedResolveStartPage(boolean mustChangePassword, SharedUserRole role) {
        if (mustChangePassword) {
            return "/shared/shared-change-password.html";
        }
        return SharedStartPageRule.sharedFindStartPage(role);
    }

    /**
     * What it does: Checks that a password can be sent safely to BCrypt and matches the stored hash.
     * Input: The supplied password and the hash.
     * Output: True for a matching password within BCrypt's byte limit.
     */
    private boolean sharedMatchPassword(String sharedMatchPasswordValue, String passwordHash) {
        return sharedMatchPasswordValue != null
                && sharedMatchPasswordValue.getBytes(StandardCharsets.UTF_8).length
                        <= SHARED_PASSWORD_MAXIMUM_UTF8_BYTE_COUNT
                && sharedLoginUserPasswordEncoder.matches(sharedMatchPasswordValue, passwordHash);
    }

    /**
     * What it does: Validates the entered passwords in order and updates the locked account row.
     * Input: The password form and account inside the transaction.
     * Output: A changed hash and session version, or a password rule error.
     */
    private void sharedSavePassword(SharedChangePasswordRequest sharedSavePasswordRequest,
            AppUser sharedSavePasswordUser) {
        String sharedChangePasswordCurrentPassword = sharedSavePasswordRequest.sharedChangePasswordCurrentPassword();
        if (!sharedMatchPassword(sharedChangePasswordCurrentPassword, sharedSavePasswordUser.passwordHash)) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_CURRENT_PASSWORD_WRONG",
                    "The current password is wrong.");
        }

        String sharedChangePasswordNewPassword = sharedSavePasswordRequest.sharedChangePasswordNewPassword();
        List<String> sharedSavePasswordFailureList = SharedPasswordRule.sharedCheckPasswordRule(
                sharedChangePasswordNewPassword);
        if (!sharedSavePasswordFailureList.isEmpty()) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_PASSWORD_RULE_FAILED",
                    sharedSavePasswordFailureList.getFirst());
        }
        if (!sharedChangePasswordNewPassword.equals(sharedSavePasswordRequest.sharedChangePasswordConfirmPassword())) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_CONFIRM_PASSWORD_DIFFERENT",
                    "The two new passwords are not the same.");
        }
        if (sharedChangePasswordNewPassword.equals(sharedChangePasswordCurrentPassword)) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_PASSWORD_RULE_FAILED",
                    "The new password must be different from the current one.");
        }

        // Save the new hash only. The session version makes older sessions fail their next check.
        sharedSavePasswordUser.passwordHash = sharedLoginUserPasswordEncoder.encode(sharedChangePasswordNewPassword);
        sharedSavePasswordUser.mustChangePassword = false;
        sharedSavePasswordUser.temporaryPasswordExpiryTime = null;
        sharedSavePasswordUser.sessionVersion++;
    }
}
