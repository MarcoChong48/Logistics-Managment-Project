package com.lms.backend.admin.picking;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCheckReasonText;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_PICKING_TASK_REASSIGNED;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.common.AdminDecidedRowResponse;
import com.lms.backend.admin.shortage.AdminShortageCaseStatus;
import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.PickingAssignmentHistory;
import com.lms.backend.shared.database.PickingAssignmentHistoryRepository;
import com.lms.backend.shared.database.PickingTaskRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUser;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.notification.SharedNotificationSender;
import com.lms.backend.shared.notification.SharedNotificationType;
import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedOrderStatus;
import com.lms.backend.shared.value.SharedUserRole;

@Service
public class AdminPickingService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final PickingTaskRepository adminPickingTaskRepository;
    private final PickingAssignmentHistoryRepository adminPickingAssignmentRepository;
    private final CustomerOrderRepository adminPickingOrderRepository;
    private final AppUserRepository adminPickingUserRepository;
    private final SharedCurrentUserReader adminPickingCurrentUserReader;
    private final SharedAuditLogWriter adminPickingAuditLogWriter;
    private final SharedNotificationSender adminPickingNotificationSender;

    /**
     * What it does: Connects picking to the database, the Admin session, audit and notification services.
     * Input: The task, assignment, order and account repositories, and the session and writer services.
     * Output: The service for the picking queue and reassignment.
     */
    public AdminPickingService(PickingTaskRepository adminPickingTaskRepository,
            PickingAssignmentHistoryRepository adminPickingAssignmentRepository,
            CustomerOrderRepository adminPickingOrderRepository, AppUserRepository adminPickingUserRepository,
            SharedCurrentUserReader adminPickingCurrentUserReader, SharedAuditLogWriter adminPickingAuditLogWriter,
            SharedNotificationSender adminPickingNotificationSender) {
        this.adminPickingTaskRepository = adminPickingTaskRepository;
        this.adminPickingAssignmentRepository = adminPickingAssignmentRepository;
        this.adminPickingOrderRepository = adminPickingOrderRepository;
        this.adminPickingUserRepository = adminPickingUserRepository;
        this.adminPickingCurrentUserReader = adminPickingCurrentUserReader;
        this.adminPickingAuditLogWriter = adminPickingAuditLogWriter;
        this.adminPickingNotificationSender = adminPickingNotificationSender;
    }

    // ===== SECTION 2: adminSearchPickingTask =====

    /**
     * What it does: Lists picking orders with an open assignment and their saved progress, oldest task first.
     * Input: The one-based page number.
     * Output: Up to 20 rows and the page counts.
     * Placeholder: PI-08 and PI-05 read shared picking rows and provisional task status words.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchPickingTaskResponse adminSearchPickingTask(int adminSearchPickingTaskPageNumber) {
        // Keep out-of-range page requests within the list, using one snapshot for the count and rows.
        int adminSearchPickingTaskRequestedPageNumber = Math.clamp(
                adminSearchPickingTaskPageNumber, 1, Integer.MAX_VALUE / ADMIN_TABLE_PAGE_SIZE);
        Page<AdminSearchPickingTaskRow> adminSearchPickingTaskPage =
                adminReadPickingTaskPage(adminSearchPickingTaskRequestedPageNumber);
        long adminSearchPickingTaskTotalRowCount = adminSearchPickingTaskPage.getTotalElements();
        int adminSearchPickingTaskTotalPageCount = adminCountPage(adminSearchPickingTaskTotalRowCount);
        int adminSearchPickingTaskActualPageNumber =
                Math.min(adminSearchPickingTaskRequestedPageNumber, adminSearchPickingTaskTotalPageCount);
        if (adminSearchPickingTaskActualPageNumber < adminSearchPickingTaskRequestedPageNumber
                && adminSearchPickingTaskTotalRowCount > 0) {
            adminSearchPickingTaskPage = adminReadPickingTaskPage(adminSearchPickingTaskActualPageNumber);
        }
        return new AdminSearchPickingTaskResponse(adminSearchPickingTaskPage.getContent(),
                adminSearchPickingTaskActualPageNumber, adminSearchPickingTaskTotalPageCount,
                adminSearchPickingTaskTotalRowCount);
    }

    // ===== SECTION 3: adminViewWarehouseWorkerWorkload =====

    /**
     * What it does: Counts active picking tasks for every active Warehouse Worker.
     * Input: None.
     * Output: Workers ordered by workload and then by username.
     * Placeholder: PI-08 reads the order and open picking assignments populated by Warehouse.
     */
    @Transactional(readOnly = true)
    public AdminViewWarehouseWorkerWorkloadResponse adminViewWarehouseWorkerWorkload() {
        // PLACEHOLDER INPUT PI-08
        // From: Warehouse.
        // What: The orders and their current picking assignments.
        // How to use: Warehouse writes the shared rows. Until then, the test tools supply them.
        return new AdminViewWarehouseWorkerWorkloadResponse(adminPickingTaskRepository
                .adminViewWarehouseWorkerWorkload(SharedUserRole.WAREHOUSE_WORKER, SharedAccountStatus.ACTIVE,
                        SharedOrderStatus.PICKING.name()));
    }

    // ===== SECTION 4: adminReassignPickingTask =====

    /**
     * What it does: Ends the old assignment, adds one for the new worker, audits it and tells both workers.
     * Input: The picking task ID, new worker ID and reason.
     * Output: The saved worker and decision details, or an input, missing task or state error.
     * Placeholder: PO-04 publishes the assignment change and PO-08 supplies the workers' notifications.
     */
    @Transactional
    public AdminDecidedRowResponse adminReassignPickingTask(
            Long pickingTaskID, AdminReassignPickingTaskRequest adminReassignPickingTaskRequest) {
        String adminReassignPickingTaskReason =
                adminCheckReasonText(adminReassignPickingTaskRequest.adminReassignPickingTaskReason());
        Long orderID = adminPickingTaskRepository.findOrderIDByPickingTaskID(pickingTaskID)
                .orElseThrow(this::adminRefuseMissingPickingTask);

        // Refuse immediately if another writer is changing the order. Then lock the task and its assignment.
        CustomerOrder adminReassignPickingTaskOrder = adminLockPickingTaskOrder(orderID);
        adminPickingTaskRepository.findByPickingTaskIDForUpdate(pickingTaskID)
                .orElseThrow(this::adminRefuseMissingPickingTask);
        PickingAssignmentHistory adminReassignPickingTaskOldAssignment =
                adminPickingAssignmentRepository.findOpenAssignmentForChange(pickingTaskID)
                        .orElseThrow(this::adminRefusePickingTaskState);
        if (!SharedOrderStatus.PICKING.name().equals(adminReassignPickingTaskOrder.orderStatus)) {
            throw adminRefusePickingTaskState();
        }

        // Lock the new worker against role change and ban until the assignment has been saved.
        AppUser adminReassignPickingTaskNewWorker = adminReadPickingReplacementWorker(
                adminReassignPickingTaskRequest.adminReassignPickingTaskNewWorkerUserID(),
                adminReassignPickingTaskOldAssignment.workerUserID);
        AppUser adminReassignPickingTaskOldWorker =
                adminPickingUserRepository.findById(adminReassignPickingTaskOldAssignment.workerUserID).orElseThrow();
        SharedCurrentUser adminReassignPickingTaskAdmin = adminPickingCurrentUserReader.sharedReadCurrentUser();

        Instant assignedTime = adminSavePickingReassignment(
                adminReassignPickingTaskOldAssignment, adminReassignPickingTaskNewWorker.userID);
        adminRecordPickingReassignment(adminReassignPickingTaskOrder, pickingTaskID,
                adminReassignPickingTaskAdmin.userID(), adminReassignPickingTaskOldWorker,
                adminReassignPickingTaskNewWorker, adminReassignPickingTaskReason);
        String adminReassignPickingTaskMessage = "The picking task of order " + adminReassignPickingTaskOrder.orderReference
                + " was reassigned to " + adminReassignPickingTaskNewWorker.username + ".";
        return new AdminDecidedRowResponse(adminReassignPickingTaskMessage, null, adminReassignPickingTaskNewWorker.username,
                adminReassignPickingTaskAdmin.username(), assignedTime, null, false);
    }

    // ===== SECTION 5: HELPER FUNCTIONS =====

    /**
     * What it does: Reads one page of picking tasks and treats both unresolved shortage states as open.
     * Input: The one-based page number.
     * Output: The selected rows and count.
     * Placeholder: PI-08 and PI-05 need the picking rows and the task status strings from Warehouse.
     */
    private Page<AdminSearchPickingTaskRow> adminReadPickingTaskPage(int adminSearchPickingTaskPageNumber) {
        // PLACEHOLDER INPUT PI-08 and PI-05
        // From: Warehouse.
        // What: Picking tasks, assignments, progress quantities and task status words.
        // How to use: Warehouse writes the shared rows. Until then the test tools supply them.
        return adminPickingTaskRepository.adminSearchPickingTask(SharedOrderStatus.PICKING.name(),
                List.of(AdminShortageCaseStatus.OPEN.name(), AdminShortageCaseStatus.WAITING_RESTOCK.name()),
                PageRequest.of(adminSearchPickingTaskPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
    }

    /**
     * What it does: Takes the picking task's order lock without waiting.
     * Input: The parent order ID.
     * Output: The locked order, or 409 when another write is holding its lock.
     */
    private CustomerOrder adminLockPickingTaskOrder(Long orderID) {
        try {
            return adminPickingOrderRepository.findByOrderIDForUpdateNowait(orderID)
                    .orElseThrow(this::adminRefusePickingTaskState);
        } catch (PessimisticLockingFailureException adminLockPickingTaskOrderFailure) {
            throw adminRefusePickingAssignmentChange();
        }
    }

    /**
     * What it does: Locks an eligible replacement worker and verifies that their assignment would change the task.
     * Input: The replacement user ID and the current worker ID.
     * Output: The eligible worker, or a worker-not-allowed conflict.
     */
    private AppUser adminReadPickingReplacementWorker(Long adminReassignPickingTaskNewWorkerUserID,
            Long workerUserID) {
        AppUser adminReadPickingReplacementWorkerAccount = adminReassignPickingTaskNewWorkerUserID == null
                ? null : adminPickingUserRepository.findByUserIDForChange(adminReassignPickingTaskNewWorkerUserID)
                        .orElse(null);
        if (adminReadPickingReplacementWorkerAccount == null
                || adminReadPickingReplacementWorkerAccount.role != SharedUserRole.WAREHOUSE_WORKER
                || adminReadPickingReplacementWorkerAccount.accountStatus != SharedAccountStatus.ACTIVE) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_WORKER_NOT_ALLOWED",
                    "Choose an active Warehouse Worker.");
        }
        if (adminReadPickingReplacementWorkerAccount.userID.equals(workerUserID)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_WORKER_NOT_ALLOWED",
                    "The task already belongs to this worker.");
        }
        return adminReadPickingReplacementWorkerAccount;
    }

    /**
     * What it does: Closes the current assignment and creates the next one at the same time.
     * Input: The locked open assignment and the new worker ID.
     * Output: The saved assignment time, or a conflict if the unique open assignment changed concurrently.
     * Placeholder: PO-04 leaves these rows for Warehouse to determine who can continue the task.
     */
    private Instant adminSavePickingReassignment(PickingAssignmentHistory adminReassignPickingTaskOldAssignment,
            Long adminReassignPickingTaskNewWorkerUserID) {
        // PLACEHOLDER OUTPUT PO-04
        // To: Warehouse.
        // What: The closed assignment and the new open assignment for unchanged picking progress.
        // How to use: Allow only the worker of the open assignment to scan, undo scans or confirm packing.
        Instant assignedTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        adminReassignPickingTaskOldAssignment.endedTime = assignedTime;
        adminPickingAssignmentRepository.saveAndFlush(adminReassignPickingTaskOldAssignment);

        PickingAssignmentHistory adminSavePickingReassignmentNewAssignment = new PickingAssignmentHistory();
        adminSavePickingReassignmentNewAssignment.pickingTaskID = adminReassignPickingTaskOldAssignment.pickingTaskID;
        adminSavePickingReassignmentNewAssignment.workerUserID = adminReassignPickingTaskNewWorkerUserID;
        adminSavePickingReassignmentNewAssignment.assignedTime = assignedTime;
        try {
            // Keep the physical unique index as a guard if Warehouse writes its own assignment concurrently.
            adminPickingAssignmentRepository.saveAndFlush(adminSavePickingReassignmentNewAssignment);
        } catch (DataIntegrityViolationException adminSavePickingReassignmentError) {
            for (Throwable adminSavePickingReassignmentCause = adminSavePickingReassignmentError;
                 adminSavePickingReassignmentCause != null;
                 adminSavePickingReassignmentCause = adminSavePickingReassignmentCause.getCause()) {
                if (adminSavePickingReassignmentCause instanceof ConstraintViolationException
                        adminSavePickingReassignmentViolation
                        && "uq_active_picking_assignment".equals(adminSavePickingReassignmentViolation
                                .getConstraintName())) {
                    throw adminRefusePickingAssignmentChange();
                }
            }
            throw adminSavePickingReassignmentError;
        }
        return assignedTime;
    }

    /**
     * What it does: Audits the old and new worker and writes one notification to each.
     * Input: The order, task, acting Admin, old worker, new worker and reason.
     * Output: One audit row and two notifications saved with the reassignment.
     * Placeholder: PO-08 publishes the unread notifications.
     */
    private void adminRecordPickingReassignment(CustomerOrder adminReassignPickingTaskOrder, Long pickingTaskID,
            Long actorUserID, AppUser adminReassignPickingTaskOldWorker, AppUser adminReassignPickingTaskNewWorker,
            String adminReassignPickingTaskReason) {
        String orderReference = adminReassignPickingTaskOrder.orderReference;
        adminPickingAuditLogWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_PICKING_TASK_REASSIGNED,
                "PICKING_TASK", pickingTaskID.toString(),
                Map.of("workerUserID", adminReassignPickingTaskOldWorker.userID,
                        "username", adminReassignPickingTaskOldWorker.username),
                Map.of("workerUserID", adminReassignPickingTaskNewWorker.userID,
                        "username", adminReassignPickingTaskNewWorker.username,
                        "reason", adminReassignPickingTaskReason, "orderReference", orderReference));

        // PLACEHOLDER OUTPUT PO-08
        // To: Warehouse.
        // What: A removal message for the old worker and an assignment message for the new worker.
        // How to use: Display each message to its recipient and let that worker mark it read.
        SharedNotificationType adminRecordPickingReassignmentRemovedType =
                SharedNotificationType.WORKER_PICKING_TASK_REMOVED;
        adminPickingNotificationSender.sharedSendNotification(adminReassignPickingTaskOldWorker.userID,
                adminReassignPickingTaskOrder.orderID, adminRecordPickingReassignmentRemovedType,
                adminRecordPickingReassignmentRemovedType.sharedFormatNotificationMessage(orderReference));
        SharedNotificationType adminRecordPickingReassignmentAssignedType =
                SharedNotificationType.WORKER_PICKING_TASK_ASSIGNED;
        adminPickingNotificationSender.sharedSendNotification(adminReassignPickingTaskNewWorker.userID,
                adminReassignPickingTaskOrder.orderID, adminRecordPickingReassignmentAssignedType,
                adminRecordPickingReassignmentAssignedType.sharedFormatNotificationMessage(orderReference));
    }

    /**
     * What it does: Gives the conflict for a task without an open picking assignment.
     * Input: None.
     * Output: The task state conflict.
     */
    private SharedRuleException adminRefusePickingTaskState() {
        return new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_PICKING_TASK_STATE_CONFLICT",
                "Only a task that is being picked can be reassigned.");
    }

    /**
     * What it does: Gives the error for a task that no longer exists.
     * Input: None.
     * Output: The picking task not-found error.
     */
    private SharedRuleException adminRefuseMissingPickingTask() {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_PICKING_TASK_NOT_FOUND", "Picking task not found.");
    }

    /**
     * What it does: Gives the conflict when another writer won the reassignment.
     * Input: None.
     * Output: The task state conflict with a request to refresh.
     */
    private SharedRuleException adminRefusePickingAssignmentChange() {
        return new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_PICKING_TASK_STATE_CONFLICT",
                "This task was just changed by someone else. Please refresh the page.");
    }
}
