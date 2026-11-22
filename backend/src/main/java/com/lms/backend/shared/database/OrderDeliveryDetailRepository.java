package com.lms.backend.shared.database;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// Shared database placeholder. Use the team's mapped repository once available.
public interface OrderDeliveryDetailRepository extends JpaRepository<OrderDeliveryDetail, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads the versions of one order's delivery details without any contact fields.
     * Input: The order ID.
     * Output: The version metadata, in version order.
     */
    List<OrderDeliveryDetail> findByOrderIDOrderByVersionNo(Long orderID);

    /**
     * What it does: Finds the version to bind to an Admin-recorded client request.
     * Input: The order ID.
     * Output: The newest version's ID, with no contact fields read.
     */
    @Query("""
            SELECT detail.deliveryDetailID FROM OrderDeliveryDetail detail
            WHERE detail.orderID = :orderID
            ORDER BY detail.versionNo DESC LIMIT 1
            """)
    Optional<Long> findLatestDeliveryDetailID(Long orderID);
}
