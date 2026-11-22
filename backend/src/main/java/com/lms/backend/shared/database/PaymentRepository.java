package com.lms.backend.shared.database;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.lms.backend.admin.order.AdminViewOrderPaymentRow;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's mapped repository once available.
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads an order's checkout states without provider references.
     * Input: The order ID.
     * Output: The public payment fields, newest first.
     */
    @Query("""
            SELECT new com.lms.backend.admin.order.AdminViewOrderPaymentRow(
                payment.paymentID, payment.amount, payment.paymentStatus, payment.createdTime,
                payment.checkoutExpiryTime, payment.acceptedTime, payment.endedTime)
            FROM Payment payment
            WHERE payment.orderID = :orderID
            ORDER BY payment.createdTime DESC, payment.paymentID DESC
            """)
    List<AdminViewOrderPaymentRow> adminViewOrderPayments(Long orderID);

    /**
     * What it does: Reads and locks the newest positive accepted payment before checking for refunds.
     * Input: The order ID.
     * Output: The latest positive payment by acceptance time and ID, or empty when nothing was paid.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT payment FROM Payment payment
            WHERE payment.orderID = :orderID AND payment.acceptedTime IS NOT NULL AND payment.amount > 0
            ORDER BY payment.acceptedTime DESC, payment.paymentID DESC
            LIMIT 1
            """)
    Optional<Payment> findPaidPaymentByOrderIDForUpdate(Long orderID);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT payment FROM Payment payment
            WHERE payment.orderID = :orderID AND payment.acceptedTime IS NOT NULL AND payment.amount > 0
            ORDER BY payment.paymentID
            """)
    List<Payment> findPaidPaymentsByOrderIDForUpdate(Long orderID);

    /**
     * What it does: Locks the payment that owns a refund before checking or retrying the attempt.
     * Input: The payment ID.
     * Output: The locked payment, or empty if it no longer exists.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM Payment payment WHERE payment.paymentID = :paymentID")
    Optional<Payment> findPaymentByPaymentIDForUpdate(Long paymentID);
}
