package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// Shared database placeholder. Use the team's mapped repository once available.
public interface OrderDeliveryDetailRepository extends JpaRepository<OrderDeliveryDetail, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads the versions of one order's delivery details without any contact fields.
     * Input: The order ID.
     * Output: The version metadata, in version order.
     */
    List<OrderDeliveryDetail> findByOrderIDOrderByVersionNo(Long orderID);
}
