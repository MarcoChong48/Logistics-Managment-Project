package com.lms.backend.shared.database;

import org.springframework.data.jpa.repository.JpaRepository;

// Shared database placeholder. Use the team's mapped PickupList repository once it is available.
public interface PickupListRepository extends JpaRepository<PickupList, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Counts claimed pickup lists that are outside the supplied status.
     * Input: The claimant's Delivery Worker ID and the excluded status.
     * Output: The number of matching pickup lists.
     */
    long countByClaimedByDeliveryWorkerIDAndStatusNot(Long claimedByDeliveryWorkerID, String status);
}
