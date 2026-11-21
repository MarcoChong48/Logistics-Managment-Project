package com.lms.backend.shared.database;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's mapped DeliveryPackage repository once it is available.
public interface DeliveryPackageRepository extends JpaRepository<DeliveryPackage, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads packages held by the selected worker outside the supplied custody status.
     * Input: The handler's user ID and the excluded custody status.
     * Output: The matching package rows.
     */
    List<DeliveryPackage> findByCurrentHandlerUserIDAndCustodyStatusNot(
            Long currentHandlerUserID, String custodyStatus);

    /**
     * What it does: Finds the order's package and its current handler's username.
     * Input: The order ID.
     * Output: One package and its handler, or an empty result.
     */
    @Query("""
            SELECT deliveryPackage, handler.username
            FROM DeliveryPackage deliveryPackage
            LEFT JOIN AppUser handler ON handler.userID = deliveryPackage.currentHandlerUserID
            WHERE deliveryPackage.orderID = :orderID
            """)
    List<Object[]> adminViewOrderPackage(Long orderID);

    /**
     * What it does: Locks the package while Admin checks whether it can cancel its order.
     * Input: The locked order's ID.
     * Output: The package, or empty when the order does not have one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryPackage> findByOrderID(Long orderID);
}
