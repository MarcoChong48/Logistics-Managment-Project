package com.lms.backend.shared.database;

import java.util.List;

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
}
