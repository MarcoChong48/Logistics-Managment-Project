package com.lms.backend.shared.login;

import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_TEMPORARY_PASSWORD_ISSUED;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

@Component
public class SharedBootstrapAdminRunner implements ApplicationRunner {

    // ===== SECTION 1: OTHER CLASSES AND SETTINGS THIS CLASS USES =====

    private final Logger sharedCreateBootstrapAdminLogger = LoggerFactory.getLogger(SharedBootstrapAdminRunner.class);
    private final AppUserRepository sharedCreateBootstrapAdminUserRepository;
    private final PasswordEncoder sharedCreateBootstrapAdminPasswordEncoder;
    private final SharedSecurityLogWriter sharedCreateBootstrapAdminSecurityLogWriter;
    private final TransactionTemplate sharedCreateBootstrapAdminTransaction;
    private final String sharedCreateBootstrapAdminUsername;
    private final String sharedCreateBootstrapAdminTemporaryPassword;
    private final boolean sharedCreateBootstrapAdminResetOnStart;

    /**
     * What it does: Receives the account services and local startup settings.
     * Input: User repository, password hasher, security log writer, transaction and bootstrap settings.
     * Output: A startup runner.
     */
    public SharedBootstrapAdminRunner(
            AppUserRepository sharedCreateBootstrapAdminUserRepository,
            PasswordEncoder sharedCreateBootstrapAdminPasswordEncoder,
            SharedSecurityLogWriter sharedCreateBootstrapAdminSecurityLogWriter,
            PlatformTransactionManager sharedCreateBootstrapAdminTransactionManager,
            @Value("${lms.shared.bootstrap-admin-username:}") String sharedCreateBootstrapAdminUsername,
            @Value("${lms.shared.bootstrap-admin-temporary-password:}")
            String sharedCreateBootstrapAdminTemporaryPassword,
            @Value("${lms.shared.bootstrap-admin-reset-on-start:false}") boolean sharedCreateBootstrapAdminResetOnStart) {
        this.sharedCreateBootstrapAdminUserRepository = sharedCreateBootstrapAdminUserRepository;
        this.sharedCreateBootstrapAdminPasswordEncoder = sharedCreateBootstrapAdminPasswordEncoder;
        this.sharedCreateBootstrapAdminSecurityLogWriter = sharedCreateBootstrapAdminSecurityLogWriter;
        this.sharedCreateBootstrapAdminTransaction =
                new TransactionTemplate(sharedCreateBootstrapAdminTransactionManager);
        this.sharedCreateBootstrapAdminUsername = sharedCreateBootstrapAdminUsername;
        this.sharedCreateBootstrapAdminTemporaryPassword = sharedCreateBootstrapAdminTemporaryPassword;
        this.sharedCreateBootstrapAdminResetOnStart = sharedCreateBootstrapAdminResetOnStart;
    }

    // ===== SECTION 2: STARTUP =====

    /**
     * What it does: Runs the Admin availability check at application startup.
     * Input: Spring Boot startup arguments.
     * Output: None.
     */
    @Override
    public void run(ApplicationArguments sharedCreateBootstrapAdminArguments) {
        sharedCreateBootstrapAdmin();
    }

    // ===== SECTION 3: sharedCreateBootstrapAdmin =====

    /**
     * What it does: Restores Admin access when no usable Admin exists or the operator requests a reset.
     * Input: None. Reads the username, temporary password and reset setting supplied at startup.
     * Output: None. Logs credential issuance and warns about an explicit reset or invalid settings.
     */
    public void sharedCreateBootstrapAdmin() {
        Map.Entry<Long, Boolean> sharedCreateBootstrapAdminResult = sharedCreateBootstrapAdminTransaction
                .execute(sharedCreateBootstrapAdminTransactionStatus -> {
            // Serialize the availability check and account write across concurrent starts.
            sharedCreateBootstrapAdminUserRepository.sharedCreateBootstrapAdminLock();
            Instant sharedCreateBootstrapAdminTime = Instant.now();
            if (!sharedCreateBootstrapAdminResetOnStart
                    && sharedCreateBootstrapAdminUserRepository.sharedCreateBootstrapAdminHasUsableAccount(
                            SharedUserRole.ADMIN, SharedAccountStatus.ACTIVE, sharedCreateBootstrapAdminTime)) {
                return null;
            }

            // Require a username and a temporary password that meet the startup rules.
            if (sharedCreateBootstrapAdminUsername.isBlank()
                    || !SharedPasswordRule.sharedCheckPasswordRule(sharedCreateBootstrapAdminTemporaryPassword)
                            .isEmpty()) {
                sharedCreateBootstrapAdminLogger.warn("Set a bootstrap Admin username and a valid temporary password.");
                return null;
            }

            // A configured name can only restore an Admin. It must never take over another role's account.
            AppUser sharedCreateBootstrapAdminUser = sharedCreateBootstrapAdminUserRepository
                    .findByUsernameForChange(sharedCreateBootstrapAdminUsername).orElseGet(AppUser::new);
            boolean sharedCreateBootstrapAdminIsRequestedReset = sharedCreateBootstrapAdminResetOnStart
                    && sharedCreateBootstrapAdminUser.userID != null;
            if (sharedCreateBootstrapAdminUser.userID == null) {
                sharedCreateBootstrapAdminUser.username = sharedCreateBootstrapAdminUsername;
                sharedCreateBootstrapAdminUser.role = SharedUserRole.ADMIN;
            } else if (sharedCreateBootstrapAdminUser.role == SharedUserRole.ADMIN) {
                sharedCreateBootstrapAdminUser.sessionVersion++;
            } else {
                sharedCreateBootstrapAdminLogger.warn("The bootstrap Admin username belongs to another role.");
                return null;
            }

            // Restore access with a hashed temporary password and a new 24-hour expiry.
            sharedCreateBootstrapAdminUser.accountStatus = SharedAccountStatus.ACTIVE;
            sharedCreateBootstrapAdminUser.passwordHash =
                    sharedCreateBootstrapAdminPasswordEncoder.encode(sharedCreateBootstrapAdminTemporaryPassword);
            sharedCreateBootstrapAdminUser.mustChangePassword = true;
            sharedCreateBootstrapAdminUser.temporaryPasswordExpiryTime = sharedCreateBootstrapAdminTime.plus(
                    SharedLoginRule.SHARED_TEMPORARY_PASSWORD_VALID_HOURS, ChronoUnit.HOURS);
            sharedCreateBootstrapAdminUser.failedLoginCount = 0;
            sharedCreateBootstrapAdminUser.lockUntil = null;
            Long userID = sharedCreateBootstrapAdminUserRepository.save(sharedCreateBootstrapAdminUser).userID;
            return Map.entry(userID, sharedCreateBootstrapAdminIsRequestedReset);
        });

        // The log's foreign key must be able to see the saved account in its own transaction.
        if (sharedCreateBootstrapAdminResult != null) {
            boolean sharedCreateBootstrapAdminWasReset = sharedCreateBootstrapAdminResult.getValue();
            sharedCreateBootstrapAdminSecurityLogWriter.sharedWriteSecurityLog(
                    sharedCreateBootstrapAdminResult.getKey(), SHARED_SECURITY_EVENT_TEMPORARY_PASSWORD_ISSUED,
                    "SUCCESS", sharedCreateBootstrapAdminWasReset ? "super admin reset on start" : "bootstrap admin");
            if (sharedCreateBootstrapAdminWasReset) {
                sharedCreateBootstrapAdminLogger.warn(
                        "The super admin was reset. Set LMS_BOOTSTRAP_ADMIN_RESET_ON_START back to false.");
            }
        }
    }
}
