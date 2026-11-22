package com.lms.backend.shared.database;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    /**
     * What it does: Finds the newest eligible return receipt for a package.
     * Input: The package ID, return-received type and the newest failed attempt's end time.
     * Output: The newest return receipt at or after that time, or empty.
     */
    @Query("""
            SELECT receipt FROM PackageCustodyEvent receipt
            WHERE receipt.packageID = :packageID AND receipt.eventType = :eventType AND receipt.eventTime >= :eventTime
            ORDER BY receipt.eventTime DESC, receipt.custodyEventID DESC LIMIT 1
            """)
    Optional<PackageCustodyEvent> findLatestReturnReceipt(Long packageID, String eventType, Instant eventTime);
}
