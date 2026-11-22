package com.lms.backend.shared.database;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's mapped repository once available.
public interface ShortageCaseRepository extends JpaRepository<ShortageCase, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads the order's shortage history and looks up its product SKU and workers.
     * Input: The order ID.
     * Output: Cases with SKU, reporter, deciding Admin and resolver, newest first.
     */
    @Query("""
            SELECT shortage, product.SKU, reporter.username, administrator.username, resolver.username
            FROM ShortageCase shortage
            JOIN Product product ON product.productID = shortage.productID
            JOIN AppUser reporter ON reporter.userID = shortage.reportedByWorkerID
            LEFT JOIN AppUser administrator ON administrator.userID = shortage.decidedByAdminID
            LEFT JOIN AppUser resolver ON resolver.userID = shortage.resolvedByWorkerID
            WHERE shortage.orderID = :orderID
            ORDER BY shortage.reportedTime DESC, shortage.shortageCaseID DESC
            """)
    List<Object[]> adminViewOrderShortageCases(Long orderID);

    // ===== SECTION 2: WAREHOUSE SHORTAGE CASES =====

    /**
     * What it does: Finds a shortage case's parent order before locking either row.
     * Input: The case ID.
     * Output: The order ID, or empty if the case does not exist.
     */
    @Query("SELECT shortage.orderID FROM ShortageCase shortage WHERE shortage.shortageCaseID = :shortageCaseID")
    Optional<Long> findOrderIDByShortageCaseID(Long shortageCaseID);

    /**
     * What it does: Reads and locks the current shortage case row before applying a transition.
     * Input: The case ID.
     * Output: The locked case, or empty if it does not exist.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT shortage FROM ShortageCase shortage WHERE shortage.shortageCaseID = :shortageCaseID")
    Optional<ShortageCase> findByShortageCaseIDForUpdate(Long shortageCaseID);

    /**
     * What it does: Reads and locks the shortage cases after their parent order is locked.
     * Input: The order ID.
     * Output: Every case of the order, locked in ID order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT shortage
            FROM ShortageCase shortage
            WHERE shortage.orderID = :orderID
            ORDER BY shortage.shortageCaseID
            """)
    List<ShortageCase> findAllByOrderIDForUpdate(Long orderID);

    /**
     * What it does: Checks for a shortage case that still blocks the order.
     * Input: The order ID and the open and waiting shortage statuses.
     * Output: True if at least one blocking case exists.
     */
    boolean existsByOrderIDAndCaseStatusIn(Long orderID, Collection<String> caseStatusList);

    /**
     * What it does: Reads shortage cases with the facts needed by their work table and export.
     * Input: An optional status filter encoded by the all-status flag, statuses and requested page.
     * Output: The case fields and safe related facts for the requested page.
     */
    @Query(value = """
            SELECT shortage, orders.orderReference, orders.orderStatus,
                CASE WHEN EXISTS (SELECT 1 FROM Payment payment WHERE payment.orderID = orders.orderID
                                 AND payment.acceptedTime IS NOT NULL AND payment.amount > 0)
                     THEN true ELSE false END,
                product.SKU, product.name, item.quantity, reporter.username, administrator.username
            FROM ShortageCase shortage
            JOIN CustomerOrder orders ON orders.orderID = shortage.orderID
            JOIN Product product ON product.productID = shortage.productID
            JOIN OrderItem item ON item.orderID = shortage.orderID AND item.productID = shortage.productID
            JOIN AppUser reporter ON reporter.userID = shortage.reportedByWorkerID
            LEFT JOIN AppUser administrator ON administrator.userID = shortage.decidedByAdminID
            WHERE :allStatuses = true OR shortage.caseStatus IN :caseStatuses
            """, countQuery = """
            SELECT COUNT(shortage)
            FROM ShortageCase shortage
            JOIN OrderItem item ON item.orderID = shortage.orderID AND item.productID = shortage.productID
            WHERE :allStatuses = true OR shortage.caseStatus IN :caseStatuses
            """)
    Page<Object[]> adminSearchShortageCase(boolean allStatuses, Collection<String> caseStatuses,
            Pageable adminSearchShortageCasePage);
}
