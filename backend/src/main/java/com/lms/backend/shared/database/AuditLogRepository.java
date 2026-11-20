package com.lms.backend.shared.database;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

// Shared database placeholder. Use the team's repository once it provides the audit mapping.
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // ===== SECTION 1: FILTERED AUDIT LOG =====

    /**
     * What it does: Finds the requested range of audit entries and looks up the actor's username.
     * Input: Optional times, username fragment, exact action, target type and ID, and the requested page.
     * Output: A page of entries and the total number of matches.
     */
    @Query(value = """
            SELECT audit.auditID, audit.timestamp, audit.actorUserID, actor.username, audit.action,
                audit.targetType, audit.targetID, cast(audit.oldValue as String), cast(audit.newValue as String),
                audit.result
            FROM AuditLog audit LEFT JOIN AppUser actor ON actor.userID = audit.actorUserID
            WHERE audit.timestamp >= COALESCE(:adminSearchAuditLogFromTime, audit.timestamp)
                AND audit.timestamp <= COALESCE(:adminSearchAuditLogToTime, audit.timestamp)
                AND LOCATE(LOWER(:adminSearchAuditLogActorUsername), LOWER(COALESCE(actor.username, 'system'))) > 0
                AND audit.action = COALESCE(:adminSearchAuditLogAction, audit.action)
                AND audit.targetType = COALESCE(:adminSearchAuditLogTargetType, audit.targetType)
                AND audit.targetID = COALESCE(:adminSearchAuditLogTargetID, audit.targetID)
            ORDER BY audit.timestamp DESC, audit.auditID DESC
            """, countQuery = """
            SELECT COUNT(audit)
            FROM AuditLog audit LEFT JOIN AppUser actor ON actor.userID = audit.actorUserID
            WHERE audit.timestamp >= COALESCE(:adminSearchAuditLogFromTime, audit.timestamp)
                AND audit.timestamp <= COALESCE(:adminSearchAuditLogToTime, audit.timestamp)
                AND LOCATE(LOWER(:adminSearchAuditLogActorUsername), LOWER(COALESCE(actor.username, 'system'))) > 0
                AND audit.action = COALESCE(:adminSearchAuditLogAction, audit.action)
                AND audit.targetType = COALESCE(:adminSearchAuditLogTargetType, audit.targetType)
                AND audit.targetID = COALESCE(:adminSearchAuditLogTargetID, audit.targetID)
            """)
    Page<Object[]> adminSearchAuditLog(Instant adminSearchAuditLogFromTime, Instant adminSearchAuditLogToTime,
            String adminSearchAuditLogActorUsername, String adminSearchAuditLogAction,
            String adminSearchAuditLogTargetType, String adminSearchAuditLogTargetID, Pageable adminSearchAuditLogPage);

    // ===== SECTION 2: LOG CLEAN-UP =====

    /**
     * What it does: Reads the most recent entry of the supplied action, even when two times match.
     * Input: The action code to look up.
     * Output: The newest matching row, or an empty result.
     */
    Optional<AuditLog> findFirstByActionOrderByTimestampDescAuditIDDesc(String action);

    /**
     * What it does: Deletes audit rows strictly older than the retention time.
     * Input: The cut-off instant.
     * Output: The number of deleted rows.
     */
    @Modifying
    @Query("DELETE FROM AuditLog audit WHERE audit.timestamp < :adminRunLogCleanupAuditCutOffTime")
    int adminDeleteOldAuditLog(Instant adminRunLogCleanupAuditCutOffTime);
}
