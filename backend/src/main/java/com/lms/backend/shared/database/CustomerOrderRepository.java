package com.lms.backend.shared.database;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerOrder> findByOrderReferenceAndClientUserID(String orderReference, Long clientUserID);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.orderID = :orderId")
    Optional<CustomerOrder> findOrderForPaymentChange(@Param("orderId") long orderId);

    @Query(value = "SELECT * FROM customer_order WHERE order_id = :orderId FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<CustomerOrder> findUnlockedOrderForExpiry(@Param("orderId") long orderId);
}
