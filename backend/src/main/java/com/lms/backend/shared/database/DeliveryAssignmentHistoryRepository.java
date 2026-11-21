package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// Shared database placeholder. Use the team's mapped repository once available.
public interface DeliveryAssignmentHistoryRepository extends JpaRepository<DeliveryAssignmentHistory, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads responsibility changes for a package with the names of their participants.
     * Input: The package ID.
     * Output: Each assignment, previous worker, new worker and deciding Admin, newest first.
     */
    @Query("""
            SELECT assignment, previousWorker.username, newWorker.username, administrator.username
            FROM DeliveryAssignmentHistory assignment
            LEFT JOIN AppUser previousWorker ON previousWorker.userID = assignment.previousWorkerUserID
            JOIN AppUser newWorker ON newWorker.userID = assignment.newWorkerUserID
            JOIN AppUser administrator ON administrator.userID = assignment.assignedByAdminID
            WHERE assignment.packageID = :packageID
            ORDER BY assignment.assignedTime DESC, assignment.assignmentID DESC
            """)
    List<Object[]> adminViewOrderDeliveryAssignments(Long packageID);
}
