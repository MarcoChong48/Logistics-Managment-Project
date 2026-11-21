package com.lms.backend.shared.database;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

// Shared database placeholder. Use the team's repository when it becomes available.
public interface PickingScanHistoryRepository extends JpaRepository<PickingScanHistory, Long> {

    // ===== SECTION 1: FILTERED SCAN HISTORY =====

    /**
     * What it does: Finds scan rows and returns a catalog SKU in place of the untrusted scanned text.
     * Input: Optional times, order and username fragments, an exact result, and the requested page.
     * Output: Safe identifier, matched item SKU and other scan fields for each result, plus the total count.
     */
    @Query(value = """
            SELECT scan.scanID, scan.scanTime, scan.pickingTaskID, customerOrder.orderReference,
                scan.workerUserID, worker.username,
                COALESCE(CASE WHEN LOWER(matchedProduct.SKU) = LOWER(scan.scannedIdentifier)
                             THEN matchedProduct.SKU END,
                    (SELECT MIN(product.SKU) FROM Product product
                     WHERE LOWER(product.SKU) = LOWER(scan.scannedIdentifier)), '[unknown code]'),
                scan.result, scan.reason, scan.originalScanID, matchedProduct.SKU, customerOrder.orderID
            FROM PickingScanHistory scan
                JOIN PickingTask task ON task.pickingTaskID = scan.pickingTaskID
                JOIN CustomerOrder customerOrder ON customerOrder.orderID = task.orderID
                JOIN AppUser worker ON worker.userID = scan.workerUserID
                LEFT JOIN OrderItem item ON item.orderItemID = scan.orderItemID
                LEFT JOIN Product matchedProduct ON matchedProduct.productID = item.productID
            WHERE scan.scanTime >= COALESCE(:adminSearchPickingScanHistoryFromTime, scan.scanTime)
                AND scan.scanTime <= COALESCE(:adminSearchPickingScanHistoryToTime, scan.scanTime)
                AND LOCATE(LOWER(:adminSearchPickingScanHistoryOrderReference),
                           LOWER(customerOrder.orderReference)) > 0
                AND LOCATE(LOWER(:adminSearchPickingScanHistoryWorkerUsername), LOWER(worker.username)) > 0
                AND cast(scan.result as String) = COALESCE(:adminSearchPickingScanHistoryResult,
                                                          cast(scan.result as String))
            ORDER BY scan.scanTime DESC, scan.scanID DESC
            """, countQuery = """
            SELECT COUNT(scan)
            FROM PickingScanHistory scan
                JOIN PickingTask task ON task.pickingTaskID = scan.pickingTaskID
                JOIN CustomerOrder customerOrder ON customerOrder.orderID = task.orderID
                JOIN AppUser worker ON worker.userID = scan.workerUserID
            WHERE scan.scanTime >= COALESCE(:adminSearchPickingScanHistoryFromTime, scan.scanTime)
                AND scan.scanTime <= COALESCE(:adminSearchPickingScanHistoryToTime, scan.scanTime)
                AND LOCATE(LOWER(:adminSearchPickingScanHistoryOrderReference),
                           LOWER(customerOrder.orderReference)) > 0
                AND LOCATE(LOWER(:adminSearchPickingScanHistoryWorkerUsername), LOWER(worker.username)) > 0
                AND cast(scan.result as String) = COALESCE(:adminSearchPickingScanHistoryResult,
                                                          cast(scan.result as String))
            """)
    Page<Object[]> adminSearchPickingScanHistory(Instant adminSearchPickingScanHistoryFromTime,
            Instant adminSearchPickingScanHistoryToTime, String adminSearchPickingScanHistoryOrderReference,
            String adminSearchPickingScanHistoryWorkerUsername, String adminSearchPickingScanHistoryResult,
            Pageable adminSearchPickingScanHistoryPage);

    // ===== SECTION 2: LOG CLEAN-UP =====

    /**
     * What it does: Makes simultaneous log clean-ups finish one at a time.
     * Input: None. The caller must already be in a transaction.
     * Output: A fixed value once this transaction owns the lock.
     */
    @Query(value = """
            SELECT 1
            FROM pg_advisory_xact_lock(hashtextextended('com.lms.backend.admin.log.AdminLogCleanupTask', 0))
            """, nativeQuery = true)
    int adminLockLogCleanup();

    /**
     * What it does: Deletes old reversals unless a kept scan still depends on them.
     * Input: The cut-off instant. A scan at or after it is kept with all of its referenced ancestors.
     * Output: The number of deleted reversals.
     */
    @Modifying
    @Query(value = """
            WITH RECURSIVE kept_scan(scan_id, original_scan_id) AS (
                SELECT scan_id, original_scan_id FROM picking_scan_history
                WHERE scan_time >= :adminRunLogCleanupPickingScanCutOffTime AND original_scan_id IS NOT NULL
                UNION
                SELECT original.scan_id, original.original_scan_id
                FROM picking_scan_history original JOIN kept_scan kept ON original.scan_id = kept.original_scan_id
            )
            DELETE FROM picking_scan_history scan
            WHERE scan.scan_time < :adminRunLogCleanupPickingScanCutOffTime
                AND scan.original_scan_id IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM kept_scan kept WHERE kept.scan_id = scan.scan_id)
            """, nativeQuery = true)
    int adminDeleteOldPickingScanReversals(Instant adminRunLogCleanupPickingScanCutOffTime);

    /**
     * What it does: Deletes old original scans after reversals, keeping any scan still referenced.
     * Input: The cut-off instant.
     * Output: The number of deleted originals.
     */
    @Modifying
    @Query(value = """
            DELETE FROM picking_scan_history scan
            WHERE scan.scan_time < :adminRunLogCleanupPickingScanCutOffTime
                AND scan.original_scan_id IS NULL
                AND NOT EXISTS (SELECT 1 FROM picking_scan_history other_scan
                                WHERE other_scan.original_scan_id = scan.scan_id)
            """, nativeQuery = true)
    int adminDeleteOldPickingScanOriginals(Instant adminRunLogCleanupPickingScanCutOffTime);
}
