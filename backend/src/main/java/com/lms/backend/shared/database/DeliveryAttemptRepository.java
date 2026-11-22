package com.lms.backend.shared.database;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// Shared database placeholder. Use the team's mapped repository once available.
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads each delivery attempt with the worker name and proof counts.
     * Input: The package ID and the two recognized proof types.
     * Output: Attempt fields and the counts. File references are never selected.
     */
    @Query("""
            SELECT attempt, worker.username,
                (SELECT COUNT(proof) FROM DeliveryProof proof
                 WHERE proof.deliveryAttemptID = attempt.deliveryAttemptID AND proof.proofType = :photoType),
                (SELECT COUNT(proof) FROM DeliveryProof proof
                 WHERE proof.deliveryAttemptID = attempt.deliveryAttemptID AND proof.proofType = :signatureType)
            FROM DeliveryAttempt attempt
            JOIN AppUser worker ON worker.userID = attempt.deliveryWorkerID
            WHERE attempt.packageID = :packageID
            ORDER BY attempt.startTime DESC, attempt.deliveryAttemptID DESC
            """)
    List<Object[]> adminViewOrderDeliveryAttempts(Long packageID, String photoType, String signatureType);

    /**
     * What it does: Reads the latest ended attempt with a given outcome.
     * Input: The package ID and the stored failed outcome.
     * Output: The attempt whose end time supplies the start of the return check.
     */
    Optional<DeliveryAttempt> findFirstByPackageIDAndOutcomeAndEndTimeIsNotNullOrderByEndTimeDescDeliveryAttemptIDDesc(
            Long packageID, String outcome);
}
