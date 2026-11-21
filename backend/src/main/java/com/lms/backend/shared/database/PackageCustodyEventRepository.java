package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// Shared database placeholder. Use the team's mapped repository once available.
public interface PackageCustodyEventRepository extends JpaRepository<PackageCustodyEvent, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads one package's custody events and their actors.
     * Input: The package ID.
     * Output: Each event with its actor's username, where recorded.
     */
    @Query("""
            SELECT custodyEvent, actor.username
            FROM PackageCustodyEvent custodyEvent
            LEFT JOIN AppUser actor ON actor.userID = custodyEvent.actorUserID
            WHERE custodyEvent.packageID = :packageID
            ORDER BY custodyEvent.eventTime, custodyEvent.custodyEventID
            """)
    List<Object[]> adminViewOrderCustodyEvents(Long packageID);
}
