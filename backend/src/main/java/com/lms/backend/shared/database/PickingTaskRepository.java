package com.lms.backend.shared.database;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.lms.backend.admin.picking.AdminSearchPickingTaskRow;
import com.lms.backend.admin.picking.AdminViewWarehouseWorkerWorkloadRow;
import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's mapped PickingTask repository once it is available.
public interface PickingTaskRepository extends JpaRepository<PickingTask, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Finds the order's picking task when it exists.
     * Input: The order ID.
     * Output: Its one picking task or an empty result.
     */
    Optional<PickingTask> findByOrderID(Long orderID);

    /**
     * What it does: Finds and locks the picking task of an order already locked by the caller.
     * Input: The order ID.
     * Output: Its locked task or an empty result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT task FROM PickingTask task WHERE task.orderID = :orderID")
    Optional<PickingTask> findByOrderIDForUpdate(Long orderID);

    /**
     * What it does: Finds the parent order before taking any write locks.
     * Input: The picking task ID.
     * Output: Its order ID or an empty result when no task exists.
     */
    @Query("SELECT task.orderID FROM PickingTask task WHERE task.pickingTaskID = :pickingTaskID")
    Optional<Long> findOrderIDByPickingTaskID(Long pickingTaskID);

    /**
     * What it does: Locks the picking task after its parent order is locked.
     * Input: The picking task ID.
     * Output: The locked task when it still exists.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT task FROM PickingTask task WHERE task.pickingTaskID = :pickingTaskID")
    Optional<PickingTask> findByPickingTaskIDForUpdate(Long pickingTaskID);

    // ===== SECTION 2: ADMIN PICKING SEARCH =====

    /**
     * What it does: Reads the picking queue with its current assignment, saved quantities and shortage flag.
     * Input: The order state, open shortage states and page request.
     * Output: One row per open assignment, oldest task first, and the total row count.
     */
    @Query(value = """
            SELECT new com.lms.backend.admin.picking.AdminSearchPickingTaskRow(
                task.pickingTaskID, orders.orderID, orders.orderReference, task.taskStatus, task.startTime,
                worker.userID, worker.username, worker.accountStatus, assignment.assignedTime,
                (SELECT COALESCE(SUM(item.pickedQuantity), 0L) FROM OrderItem item WHERE item.orderID = orders.orderID),
                (SELECT COALESCE(SUM(item.quantity), 0L) FROM OrderItem item WHERE item.orderID = orders.orderID),
                CASE WHEN EXISTS (SELECT 1 FROM ShortageCase shortage
                                  WHERE shortage.orderID = orders.orderID AND shortage.caseStatus IN :caseStatusList)
                     THEN true ELSE false END)
            FROM PickingTask task
            JOIN CustomerOrder orders ON orders.orderID = task.orderID
            JOIN PickingAssignmentHistory assignment ON assignment.pickingTaskID = task.pickingTaskID
            JOIN AppUser worker ON worker.userID = assignment.workerUserID
            WHERE orders.orderStatus = :orderStatus AND assignment.endedTime IS NULL
            ORDER BY task.startTime ASC NULLS LAST, task.pickingTaskID ASC
            """, countQuery = """
            SELECT COUNT(task)
            FROM PickingTask task
            JOIN CustomerOrder orders ON orders.orderID = task.orderID
            JOIN PickingAssignmentHistory assignment ON assignment.pickingTaskID = task.pickingTaskID
            WHERE orders.orderStatus = :orderStatus AND assignment.endedTime IS NULL
            """)
    Page<AdminSearchPickingTaskRow> adminSearchPickingTask(
            String orderStatus, Collection<String> caseStatusList, Pageable adminSearchPickingTaskPage);

    /**
     * What it does: Counts picking orders with open assignments, using the user impact rule.
     * Input: The role and state required of workers and the order state that counts as active work.
     * Output: Every eligible worker, with workers who have fewer active tasks first.
     */
    @Query("""
            SELECT new com.lms.backend.admin.picking.AdminViewWarehouseWorkerWorkloadRow(
                worker.userID, worker.username, COUNT(orders.orderID))
            FROM AppUser worker
            LEFT JOIN PickingAssignmentHistory assignment ON assignment.workerUserID = worker.userID
                AND assignment.endedTime IS NULL
            LEFT JOIN PickingTask task ON task.pickingTaskID = assignment.pickingTaskID
            LEFT JOIN CustomerOrder orders ON orders.orderID = task.orderID AND orders.orderStatus = :orderStatus
            WHERE worker.role = :role AND worker.accountStatus = :accountStatus
            GROUP BY worker.userID, worker.username
            ORDER BY COUNT(orders.orderID), worker.username
            """)
    List<AdminViewWarehouseWorkerWorkloadRow> adminViewWarehouseWorkerWorkload(
            SharedUserRole role, SharedAccountStatus accountStatus, String orderStatus);
}
