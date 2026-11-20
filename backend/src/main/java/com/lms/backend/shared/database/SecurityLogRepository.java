package com.lms.backend.shared.database;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Shared database placeholder. Use the team's mapped SecurityLog repository once it is available.
public interface SecurityLogRepository extends JpaRepository<SecurityLog, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Finds the newest event time of the requested kind for each selected account.
     * Input: The account IDs from the current page and the event word.
     * Output: A user ID and time for each account that has a matching event.
     */
    @Query("""
            SELECT event.userID, MAX(event.timestamp)
            FROM SecurityLog event
            WHERE event.eventType = :eventType AND event.userID IN :adminSearchUserIDList
            GROUP BY event.userID
            """)
    List<Object[]> findLatestEventTimes(@Param("adminSearchUserIDList") Collection<Long> adminSearchUserIDList,
            @Param("eventType") String eventType);

    // ===== SECTION 2: FILTERED SECURITY LOG =====

    /**
     * What it does: Finds the requested range of security entries with the username, when known.
     * Input: Optional times, username fragment, exact event and result, and the requested page.
     * Output: A page of entries and the total number of matches.
     */
    @Query(value = """
            SELECT event, account.username
            FROM SecurityLog event LEFT JOIN AppUser account ON account.userID = event.userID
            WHERE event.timestamp >= COALESCE(:adminSearchSecurityLogFromTime, event.timestamp)
                AND event.timestamp <= COALESCE(:adminSearchSecurityLogToTime, event.timestamp)
                AND LOCATE(LOWER(:adminSearchSecurityLogUsername), LOWER(COALESCE(account.username, ''))) > 0
                AND event.eventType = COALESCE(:adminSearchSecurityLogEventType, event.eventType)
                AND event.result = COALESCE(:adminSearchSecurityLogResult, event.result)
            ORDER BY event.timestamp DESC, event.securityLogID DESC
            """, countQuery = """
            SELECT COUNT(event)
            FROM SecurityLog event LEFT JOIN AppUser account ON account.userID = event.userID
            WHERE event.timestamp >= COALESCE(:adminSearchSecurityLogFromTime, event.timestamp)
                AND event.timestamp <= COALESCE(:adminSearchSecurityLogToTime, event.timestamp)
                AND LOCATE(LOWER(:adminSearchSecurityLogUsername), LOWER(COALESCE(account.username, ''))) > 0
                AND event.eventType = COALESCE(:adminSearchSecurityLogEventType, event.eventType)
                AND event.result = COALESCE(:adminSearchSecurityLogResult, event.result)
            """)
    Page<Object[]> adminSearchSecurityLog(Instant adminSearchSecurityLogFromTime, Instant adminSearchSecurityLogToTime,
            String adminSearchSecurityLogUsername, String adminSearchSecurityLogEventType,
            String adminSearchSecurityLogResult, Pageable adminSearchSecurityLogPage);

    // ===== SECTION 3: LOG CLEAN-UP =====

    /**
     * What it does: Deletes security rows strictly older than the retention time.
     * Input: The cut-off instant.
     * Output: The number of deleted rows.
     */
    @Modifying
    @Query("DELETE FROM SecurityLog event WHERE event.timestamp < :adminRunLogCleanupSecurityCutOffTime")
    int adminDeleteOldSecurityLog(Instant adminRunLogCleanupSecurityCutOffTime);
}
