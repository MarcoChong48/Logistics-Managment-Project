package com.lms.backend.shared.database;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

// Shared database placeholder. Use the team's mapped CustomerOrder repository once it is available.
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Counts the client's orders outside the supplied terminal states.
     * Input: The client ID and the terminal order statuses.
     * Output: The number of open orders.
     */
    long countByClientUserIDAndOrderStatusNotIn(Long clientUserID, Collection<String> orderStatus);
}
