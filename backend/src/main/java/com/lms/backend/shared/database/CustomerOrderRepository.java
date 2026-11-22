package com.lms.backend.shared.database;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.lms.backend.admin.dashboard.AdminViewDashboardOrderStatusCount;
import com.lms.backend.admin.order.AdminSearchOrderRow;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's mapped CustomerOrder repository once it is available.
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Counts the client's orders outside the supplied terminal states.
     * Input: The client ID and the terminal order statuses.
     * Output: The number of open orders.
     */
    long countByClientUserIDAndOrderStatusNotIn(Long clientUserID, Collection<String> orderStatus);

    /**
     * What it does: Reads and locks an order before work changes the order or its related rows.
     * Input: The order ID.
     * Output: The locked order, or empty when it does not exist.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT orders FROM CustomerOrder orders WHERE orders.orderID = :orderID")
    Optional<CustomerOrder> findByOrderIDForUpdate(Long orderID);

    /**
     * What it does: Locks an order without waiting for another writer.
     * Input: The order ID.
     * Output: The locked order, or empty when missing. An existing lock raises a lock conflict.
     */
    @Query(value = "SELECT * FROM customer_order WHERE order_id = :orderID FOR UPDATE NOWAIT", nativeQuery = true)
    Optional<CustomerOrder> findByOrderIDForUpdateNowait(Long orderID);

    // ===== SECTION 2: ADMIN ORDER SEARCH =====

    /**
     * What it does: Reads matching orders with separate latest payment and refund states.
     * Input: Search filters, the open case states and the requested page.
     * Output: A page of order rows and the total count.
     */
    @Query(value = """
            SELECT new com.lms.backend.admin.order.AdminSearchOrderRow(
                orders.orderID, orders.orderReference, orders.clientUserID, client.username,
                orders.orderStatus, orders.createdTime, orders.paymentDeadline, orders.totalAmount,
                (SELECT payment.paymentStatus FROM Payment payment
                 WHERE payment.orderID = orders.orderID
                 ORDER BY payment.createdTime DESC, payment.paymentID DESC LIMIT 1),
                (SELECT refund.refundStatus FROM Refund refund
                 JOIN Payment payment ON payment.paymentID = refund.paymentID
                 WHERE payment.orderID = orders.orderID
                 ORDER BY refund.createdTime DESC, refund.refundID DESC LIMIT 1),
                CASE WHEN EXISTS (SELECT 1 FROM ShortageCase shortage
                                  WHERE shortage.orderID = orders.orderID AND shortage.caseStatus IN :caseStatusList)
                          OR EXISTS (SELECT 1 FROM ClientInstruction instruction
                                     WHERE instruction.orderID = orders.orderID AND instruction.status = :status)
                     THEN true ELSE false END)
            FROM CustomerOrder orders
            JOIN AppUser client ON client.userID = orders.clientUserID
            WHERE (LOCATE(LOWER(:adminSearchOrderKeyword), LOWER(orders.orderReference)) > 0
                OR LOCATE(LOWER(:adminSearchOrderKeyword), LOWER(client.username)) > 0)
                AND orders.orderStatus = COALESCE(:adminSearchOrderOrderStatus, orders.orderStatus)
                AND orders.createdTime >= COALESCE(:adminSearchOrderCreatedFromTime, orders.createdTime)
                AND orders.createdTime <= COALESCE(:adminSearchOrderCreatedToTime, orders.createdTime)
            ORDER BY orders.createdTime DESC, orders.orderID DESC
            """, countQuery = """
            SELECT COUNT(orders)
            FROM CustomerOrder orders
            JOIN AppUser client ON client.userID = orders.clientUserID
            WHERE (LOCATE(LOWER(:adminSearchOrderKeyword), LOWER(orders.orderReference)) > 0
                OR LOCATE(LOWER(:adminSearchOrderKeyword), LOWER(client.username)) > 0)
                AND orders.orderStatus = COALESCE(:adminSearchOrderOrderStatus, orders.orderStatus)
                AND orders.createdTime >= COALESCE(:adminSearchOrderCreatedFromTime, orders.createdTime)
                AND orders.createdTime <= COALESCE(:adminSearchOrderCreatedToTime, orders.createdTime)
            """)
    Page<AdminSearchOrderRow> adminSearchOrder(String adminSearchOrderKeyword, String adminSearchOrderOrderStatus,
            Instant adminSearchOrderCreatedFromTime, Instant adminSearchOrderCreatedToTime,
            Collection<String> caseStatusList, String status, Pageable adminSearchOrderPage);

    /**
     * What it does: Counts the orders shown in the order list by their stored status.
     * Input: None.
     * Output: A count for each status found in the database.
     */
    @Query("""
            SELECT new com.lms.backend.admin.dashboard.AdminViewDashboardOrderStatusCount(
                orders.orderStatus, COUNT(orders))
            FROM CustomerOrder orders
            JOIN AppUser client ON client.userID = orders.clientUserID
            GROUP BY orders.orderStatus
            """)
    List<AdminViewDashboardOrderStatusCount> adminViewDashboardOrderStatusCount();

    /**
     * What it does: Reads the order's client username.
     * Input: The client user ID.
     * Output: The username from the linked account.
     */
    @Query("SELECT client.username FROM AppUser client WHERE client.userID = :clientUserID")
    String adminViewOrderClientUsername(Long clientUserID);

    // ===== SECTION 3: FAILED DELIVERY CASE FACTS =====

    /**
     * What it does: Reads the public facts for each failed order, before the service applies its filter.
     * Input: The stored order, attempt, custody, instruction, reminder and shortage words.
     * Output: One row per failed order, oldest failure first, with no delivery details or proof fields.
     */
    @Query("""
            SELECT orders.orderID, orders.orderReference,
                (SELECT COUNT(attempt) FROM DeliveryAttempt attempt
                 WHERE attempt.packageID = deliveryPackage.packageID AND attempt.outcome = :failedOutcome),
                latestFailure.failureReason, latestFailure.endTime, latestFailure.internalNote, worker.username,
                returnEvent.eventTime, pendingInstruction.instructionID, pendingInstruction.instructionType,
                pendingInstruction.source, pendingInstruction.submittedTime,
                (SELECT MAX(reminder.createdTime) FROM Notification reminder
                 WHERE reminder.orderID = orders.orderID AND reminder.type = :reminderType),
                CASE WHEN EXISTS (SELECT 1 FROM ShortageCase shortage
                                  WHERE shortage.orderID = orders.orderID AND shortage.caseStatus IN :caseStatusList)
                     THEN true ELSE false END,
                CASE WHEN EXISTS (SELECT 1 FROM Payment payment WHERE payment.orderID = orders.orderID
                                  AND payment.acceptedTime IS NOT NULL AND payment.amount > 0)
                     THEN true ELSE false END
            FROM CustomerOrder orders
            LEFT JOIN DeliveryPackage deliveryPackage ON deliveryPackage.orderID = orders.orderID
            LEFT JOIN DeliveryAttempt latestFailure ON latestFailure.deliveryAttemptID = (
                SELECT attempt.deliveryAttemptID FROM DeliveryAttempt attempt
                WHERE attempt.packageID = deliveryPackage.packageID AND attempt.outcome = :failedOutcome
                ORDER BY attempt.endTime DESC NULLS LAST, attempt.deliveryAttemptID DESC LIMIT 1)
            LEFT JOIN AppUser worker ON worker.userID = latestFailure.deliveryWorkerID
            LEFT JOIN PackageCustodyEvent returnEvent ON deliveryPackage.custodyStatus = :warehouseCustody
                AND returnEvent.custodyEventID = (
                    SELECT receipt.custodyEventID FROM PackageCustodyEvent receipt
                    WHERE receipt.packageID = deliveryPackage.packageID AND receipt.eventType = :returnEventType
                        AND receipt.eventTime >= latestFailure.endTime
                    ORDER BY receipt.eventTime DESC, receipt.custodyEventID DESC LIMIT 1)
            LEFT JOIN ClientInstruction pendingInstruction ON pendingInstruction.instructionID = (
                SELECT instruction.instructionID FROM ClientInstruction instruction
                WHERE instruction.orderID = orders.orderID AND instruction.status = :pendingStatus
                ORDER BY instruction.submittedTime DESC, instruction.instructionID DESC LIMIT 1)
            WHERE orders.orderStatus = :orderStatus
            ORDER BY latestFailure.endTime ASC NULLS LAST, orders.orderID ASC
            """)
    List<Object[]> adminSearchFailedDeliveryCase(String orderStatus, String failedOutcome, String warehouseCustody,
            String returnEventType, String pendingStatus, String reminderType, Collection<String> caseStatusList);
}
