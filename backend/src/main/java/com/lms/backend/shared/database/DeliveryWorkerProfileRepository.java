package com.lms.backend.shared.database;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// Shared database placeholder. Use the team's mapped DeliveryWorkerProfile repository once it is available.
public interface DeliveryWorkerProfileRepository extends JpaRepository<DeliveryWorkerProfile, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Finds a profile that already uses the supplied employee number.
     * Input: The nonempty employee number.
     * Output: The matching profile if the number is assigned.
     */
    Optional<DeliveryWorkerProfile> findByEmployeeNumber(String employeeNumber);
}
