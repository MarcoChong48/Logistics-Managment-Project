package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Shared database placeholder. Use the team's repository when it becomes available.
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // ===== SECTION 1: PRODUCT LISTING IMPACT =====

    /**
     * What it does: Counts open orders and orders awaiting payment that contain one product.
     * Input: The product ID.
     * Output: One row with the open and awaiting payment counts.
     */
    @Query(value = """
            SELECT COUNT(*) FILTER (WHERE customer_order.order_status NOT IN ('DELIVERED', 'CANCELLED')),
                   COUNT(*) FILTER (WHERE customer_order.order_status = 'AWAITING_PAYMENT')
            FROM order_item
            JOIN customer_order USING (order_id)
            WHERE order_item.product_id = :productID
            """, nativeQuery = true)
    List<Object[]> adminPreviewProductImpact(@Param("productID") Long productID);
}
