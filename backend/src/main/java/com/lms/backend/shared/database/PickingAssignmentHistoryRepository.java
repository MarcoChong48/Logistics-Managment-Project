package com.lms.backend.shared.database;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.lms.backend.admin.order.AdminViewOrderPickingAssignmentRow;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's mapped PickingAssignmentHistory repository once it is available.
public interface PickingAssignmentHistoryRepository extends JpaRepository<PickingAssignmentHistory, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads picking assignments whose end time is empty.
     * Input: The assigned Warehouse Worker ID.
     * Output: The matching assignment rows.
     */
    List<PickingAssignmentHistory> findByWorkerUserIDAndEndedTimeIsNull(Long workerUserID);

    /**
     * What it does: Reads the workers who have been assigned to a picking task.
     * Input: The picking task ID.
     * Output: Public assignment fields and usernames, newest first.
     */
    @Query("""
            SELECT new com.lms.backend.admin.order.AdminViewOrderPickingAssignmentRow(
                assignment.assignmentID, assignment.workerUserID, worker.username,
                assignment.assignedTime, assignment.endedTime)
            FROM PickingAssignmentHistory assignment
            JOIN AppUser worker ON worker.userID = assignment.workerUserID
            WHERE assignment.pickingTaskID = :pickingTaskID
            ORDER BY assignment.assignedTime DESC, assignment.assignmentID DESC
            """)
    List<AdminViewOrderPickingAssignmentRow> adminViewOrderPickingAssignments(Long pickingTaskID);

    // ===== SECTION 2: CURRENT ASSIGNMENT =====

    /**
     * What it does: Reads and locks the open assignment after its order was locked.
     * Input: The picking task ID.
     * Output: The still-open assignment, or empty when it closed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment FROM PickingAssignmentHistory assignment
            WHERE assignment.pickingTaskID = :pickingTaskID AND assignment.endedTime IS NULL
            """)
    Optional<PickingAssignmentHistory> findOpenAssignmentForChange(Long pickingTaskID);
}
