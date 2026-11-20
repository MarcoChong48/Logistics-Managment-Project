package com.lms.backend.admin.log;

import static com.lms.backend.admin.log.AdminLogRule.ADMIN_LOG_CLEANUP_FIRST_DELAY_MINUTES;
import static com.lms.backend.admin.log.AdminLogRule.ADMIN_LOG_CLEANUP_REPEAT_HOURS;
import static com.lms.backend.admin.log.AdminLogRule.adminFindAuditLogCutOffTime;
import static com.lms.backend.admin.log.AdminLogRule.adminFindPickingScanHistoryCutOffTime;
import static com.lms.backend.admin.log.AdminLogRule.adminFindSecurityLogCutOffTime;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_LOG_CLEANUP_RUN;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.shared.database.AuditLogRepository;
import com.lms.backend.shared.database.PickingScanHistoryRepository;
import com.lms.backend.shared.database.SecurityLogRepository;
import com.lms.backend.shared.log.SharedAuditLogWriter;

@Component
@EnableScheduling
public class AdminLogCleanupTask {

    // ===== SECTION 1: CONSTANTS =====

    private static final Logger ADMIN_LOG_CLEANUP_LOGGER = LoggerFactory.getLogger(AdminLogCleanupTask.class);

    // ===== SECTION 2: OTHER CLASSES THIS CLASS USES =====

    private final AuditLogRepository adminRunLogCleanupAuditRepository;
    private final SecurityLogRepository adminRunLogCleanupSecurityRepository;
    private final PickingScanHistoryRepository adminRunLogCleanupPickingScanRepository;
    private final SharedAuditLogWriter adminRunLogCleanupAuditWriter;
    private final TransactionTemplate adminRunLogCleanupTransaction;

    /**
     * What it does: Connects the clean-up job to the three log tables and the audit writer.
     * Input: Log repositories, writer, and the transaction manager.
     * Output: A task that can clean all three logs as one change.
     */
    public AdminLogCleanupTask(AuditLogRepository adminRunLogCleanupAuditRepository,
            SecurityLogRepository adminRunLogCleanupSecurityRepository,
            PickingScanHistoryRepository adminRunLogCleanupPickingScanRepository,
            SharedAuditLogWriter adminRunLogCleanupAuditWriter,
            PlatformTransactionManager adminRunLogCleanupTransactionManager) {
        this.adminRunLogCleanupAuditRepository = adminRunLogCleanupAuditRepository;
        this.adminRunLogCleanupSecurityRepository = adminRunLogCleanupSecurityRepository;
        this.adminRunLogCleanupPickingScanRepository = adminRunLogCleanupPickingScanRepository;
        this.adminRunLogCleanupAuditWriter = adminRunLogCleanupAuditWriter;
        this.adminRunLogCleanupTransaction = new TransactionTemplate(adminRunLogCleanupTransactionManager);
    }

    // ===== SECTION 3: adminRunLogCleanup =====

    /**
     * What it does: Trims the three logs together and saves the deletion counts as an audit event.
     * Input: None. The caller starts outside a transaction.
     * Output: None. A failure rolls back every deletion and is passed to the caller.
     */
    public void adminRunLogCleanup() {
        adminRunLogCleanupTransaction.executeWithoutResult(adminRunLogCleanupStatus -> {
            // Wait for any overlapping run before choosing the current retention time.
            adminRunLogCleanupPickingScanRepository.adminLockLogCleanup();
            Instant adminRunLogCleanupTime = Instant.now();
            Instant adminRunLogCleanupPickingScanCutOffTime =
                    adminFindPickingScanHistoryCutOffTime(adminRunLogCleanupTime);

            // Remove referencing scans before originals so that their foreign keys remain valid.
            int securityLogDeletedCount = adminRunLogCleanupSecurityRepository.adminDeleteOldSecurityLog(
                    adminFindSecurityLogCutOffTime(adminRunLogCleanupTime));
            int pickingScanHistoryDeletedCount = adminRunLogCleanupPickingScanRepository
                    .adminDeleteOldPickingScanReversals(adminRunLogCleanupPickingScanCutOffTime);
            pickingScanHistoryDeletedCount += adminRunLogCleanupPickingScanRepository
                    .adminDeleteOldPickingScanOriginals(adminRunLogCleanupPickingScanCutOffTime);
            int auditLogDeletedCount = adminRunLogCleanupAuditRepository.adminDeleteOldAuditLog(
                    adminFindAuditLogCutOffTime(adminRunLogCleanupTime));

            // The status row is committed only if every delete succeeds.
            adminRunLogCleanupAuditWriter.sharedWriteAuditLog(null, SHARED_AUDIT_ACTION_LOG_CLEANUP_RUN,
                    "LOG", "LOG_CLEANUP", null, Map.of("securityLogDeletedCount", securityLogDeletedCount,
                            "pickingScanHistoryDeletedCount", pickingScanHistoryDeletedCount,
                            "auditLogDeletedCount", auditLogDeletedCount));
        });
    }

    // ===== SECTION 4: SCHEDULED CLEAN-UP =====

    /**
     * What it does: Runs clean-up automatically and records a short server message if it fails.
     * Input: None. The timer starts five minutes after the application starts.
     * Output: None. The next daily run stays scheduled after a failure.
     */
    @Scheduled(initialDelay = ADMIN_LOG_CLEANUP_FIRST_DELAY_MINUTES,
            fixedRate = ADMIN_LOG_CLEANUP_REPEAT_HOURS * 60, timeUnit = TimeUnit.MINUTES)
    public void adminRunScheduledLogCleanup() {
        try {
            adminRunLogCleanup();
        } catch (RuntimeException adminRunLogCleanupFailure) {
            ADMIN_LOG_CLEANUP_LOGGER.error("Log clean-up failed. Database changes were rolled back. {}",
                    adminRunLogCleanupFailure.getClass().getSimpleName());
        }
    }
}
