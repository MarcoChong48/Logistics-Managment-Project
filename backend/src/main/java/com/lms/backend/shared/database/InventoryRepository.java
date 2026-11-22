package com.lms.backend.shared.database;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's repository when it becomes available.
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Releases reserved units without changing physical stock or going below zero.
     * Input: The product ID, released quantity and update time.
     * Output: The number of inventory rows updated.
     */
    @Modifying
    @Query(value = """
            UPDATE inventory SET reserved_stock = GREATEST(0, reserved_stock - :quantity), updated_at = :updatedTime
            WHERE product_id = :productID
            """, nativeQuery = true)
    int adminCancelOrderReleaseReservation(Long productID, int quantity, Instant updatedTime);

    @Modifying
    @Query(value = """
            UPDATE inventory SET reserved_stock = reserved_stock - :quantity, updated_at = :updatedTime
            WHERE product_id = :productID AND reserved_stock >= :quantity
            """, nativeQuery = true)
    int releaseExactReservation(Long productID, int quantity, Instant updatedTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> findByProductIDInOrderByProductIDAsc(Collection<Long> productIds);
}
