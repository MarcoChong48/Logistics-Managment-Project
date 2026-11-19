package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// Shared database placeholder. Use the team's mapped PickingAssignmentHistory repository once it is available.
public interface PickingAssignmentHistoryRepository extends JpaRepository<PickingAssignmentHistory, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads picking assignments whose end time is empty.
     * Input: The assigned Warehouse Worker ID.
     * Output: The matching assignment rows.
     */
    List<PickingAssignmentHistory> findByWorkerUserIDAndEndedTimeIsNull(Long workerUserID);
}
