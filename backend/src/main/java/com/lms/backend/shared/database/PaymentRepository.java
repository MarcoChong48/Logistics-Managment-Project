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
     * What it does: Reads and locks the newest accepted payment before checking for refunds.
     * Input: The order ID.
     * Output: The latest accepted payment by creation time and ID, or empty when none was accepted.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findFirstByOrderIDAndAcceptedTimeIsNotNullOrderByCreatedTimeDescPaymentIDDesc(Long orderID);
}
