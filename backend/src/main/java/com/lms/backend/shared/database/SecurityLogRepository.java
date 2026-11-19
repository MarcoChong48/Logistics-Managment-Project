package com.lms.backend.shared.database;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
