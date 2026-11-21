package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's mapped repository once available.
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Locks each reservation for an item that still has the supplied status.
     * Input: The order item ID and required status.
     * Output: Matching reservations, ordered by ID.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StockReservation> findByOrderItemIDAndStatusOrderByReservationIDAsc(Long orderItemID, String status);
}
