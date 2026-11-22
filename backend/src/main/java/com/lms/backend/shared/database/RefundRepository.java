package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.lms.backend.admin.order.AdminViewOrderRefundRow;

// Shared database placeholder. Use the team's mapped repository once available.
public interface RefundRepository extends JpaRepository<Refund, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads refund history across every payment of the order without provider references.
     * Input: The order ID.
     * Output: The public refund fields, newest first.
     */
    @Query("""
            SELECT new com.lms.backend.admin.order.AdminViewOrderRefundRow(
                refund.refundID, refund.paymentID, refund.amount, refund.refundStatus, refund.createdTime,
                refund.completedTime)
            FROM Refund refund
            JOIN Payment payment ON payment.paymentID = refund.paymentID
            WHERE payment.orderID = :orderID
            ORDER BY refund.createdTime DESC, refund.refundID DESC
            """)
    List<AdminViewOrderRefundRow> adminViewOrderRefunds(Long orderID);

    /**
     * What it does: Reads all refund attempts for the payment after the caller locks that payment.
     * Input: The locked payment ID.
     * Output: The attempts, newest first with ID breaking ties.
     */
    List<Refund> findByPaymentIDOrderByCreatedTimeDescRefundIDDesc(Long paymentID);

    /**
     * What it does: Reads a sorted slice of refund history with the latest check and a replacement ID.
     * Input: Whether to keep only unresolved rows, the stored words and the page.
     * Output: The public data of the matching rows with the oldest attempts first.
     */
    @Query(value = """
            SELECT refund.refundID, refund.paymentID, orders.orderID, orders.orderReference, orders.orderStatus,
                refund.amount, refund.refundStatus, refund.createdTime, refund.completedTime, audit.timestamp,
                CAST(function('jsonb_extract_path_text', audit.newValue, 'providerOutcome') AS String),
                (SELECT MAX(replacement.refundID) FROM Refund replacement
                 WHERE replacement.paymentID = refund.paymentID AND replacement.refundID > refund.refundID)
            FROM Refund refund
            JOIN Payment payment ON payment.paymentID = refund.paymentID
            JOIN CustomerOrder orders ON orders.orderID = payment.orderID
            LEFT JOIN AuditLog audit ON audit.auditID = (
                SELECT latest.auditID FROM AuditLog latest
                WHERE latest.action = :adminSearchRefundCheckAction AND latest.targetType = 'REFUND'
                    AND latest.targetID = CAST(refund.refundID AS String)
                ORDER BY latest.timestamp DESC, latest.auditID DESC LIMIT 1)
            WHERE :adminSearchRefundIsUnresolvedOnly = false OR (
                refund.refundStatus <> :adminSearchRefundCompletedStatus
                AND NOT EXISTS (SELECT 1 FROM Refund newer
                                WHERE newer.paymentID = refund.paymentID AND newer.refundID > refund.refundID))
            ORDER BY refund.createdTime, refund.refundID
            """, countQuery = """
            SELECT COUNT(refund) FROM Refund refund
            WHERE :adminSearchRefundIsUnresolvedOnly = false OR (
                refund.refundStatus <> :adminSearchRefundCompletedStatus
                AND NOT EXISTS (SELECT 1 FROM Refund newer
                                WHERE newer.paymentID = refund.paymentID AND newer.refundID > refund.refundID))
            """)
    Page<Object[]> adminSearchRefund(boolean adminSearchRefundIsUnresolvedOnly, String adminSearchRefundCompletedStatus,
            String adminSearchRefundCheckAction, Pageable adminSearchRefundPage);
}
