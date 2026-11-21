package com.lms.backend.shared.database;

import java.util.List;
import java.util.Optional;

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

    // ===== SECTION 2: ADMIN ORDER DETAIL =====

    /**
     * What it does: Reads each order item together with its catalogue SKU and name.
     * Input: The order ID.
     * Output: The item, SKU and product name, in item order.
     */
    @Query("""
            SELECT item, product.SKU, product.name
            FROM OrderItem item
            JOIN Product product ON product.productID = item.productID
            WHERE item.orderID = :orderID
            ORDER BY item.orderItemID
            """)
    List<Object[]> adminViewOrderItems(Long orderID);

    /**
     * What it does: Finds an order line for the product named in a shortage report.
     * Input: An order ID and a product ID.
     * Output: Its matching line, or empty if that product was not ordered.
     */
    Optional<OrderItem> findByOrderIDAndProductID(Long orderID, Long productID);

    /**
     * What it does: Reads the order lines in product order for inventory updates.
     * Input: The order ID.
     * Output: The order's lines, sorted by their product IDs.
     */
    List<OrderItem> findByOrderIDOrderByProductIDAsc(Long orderID);
}
