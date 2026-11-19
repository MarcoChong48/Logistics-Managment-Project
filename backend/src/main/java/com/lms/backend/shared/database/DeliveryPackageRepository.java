package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
