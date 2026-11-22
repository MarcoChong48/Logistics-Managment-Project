package com.lms.backend.admin.delivery;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCheckReasonText;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_DELIVERY_REASSIGNED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_WAREHOUSE;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.common.AdminDecidedRowResponse;
import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.database.DeliveryAssignmentHistory;
import com.lms.backend.shared.database.DeliveryAssignmentHistoryRepository;
import com.lms.backend.shared.database.DeliveryPackageRepository;
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
public class AdminHeldPackageService {

    // ===== SECTION 1: CONSTANTS =====

    // One selection for the table, the worker counts, and the reassignment state check.
    private static final String ADMIN_HELD_PACKAGE_QUERY = """
            WITH held AS (
                SELECT package.package_id, package.package_qr_code, orders.order_id,
                       orders.order_reference, orders.order_status, package.current_handler_user_id,
                       holder.username AS handler_username, holder.account_status AS handler_account_status,
                       responsible.user_id AS responsible_user_id, responsible.username AS responsible_username,
                       assignment.assigned_at
                FROM delivery_package package
                JOIN customer_order orders ON orders.order_id = package.order_id
                JOIN app_user holder ON holder.user_id = package.current_handler_user_id
                LEFT JOIN LATERAL (
                    SELECT history.new_worker_user_id, history.assigned_at
                    FROM delivery_assignment_history history
                    WHERE history.package_id = package.package_id
                    ORDER BY history.assigned_at DESC, history.assignment_id DESC
                    LIMIT 1
                ) assignment ON true
                JOIN app_user responsible
                    ON responsible.user_id = COALESCE(assignment.new_worker_user_id, holder.user_id)
                WHERE package.custody_status <> :warehouseCustodyStatus
                    AND orders.order_status IN (:heldOrderStatusList)
                    AND holder.role = :deliveryWorkerRole
            )
            """;

    // ===== SECTION 2: OTHER CLASSES THIS CLASS USES =====

    private final JdbcClient adminHeldPackageDatabase;
    private final DeliveryPackageRepository adminHeldPackageRepository;
    private final DeliveryAssignmentHistoryRepository adminHeldPackageAssignmentRepository;
    private final AppUserRepository adminHeldPackageUserRepository;
    private final SharedCurrentUserReader adminHeldPackageCurrentUserReader;
    private final SharedAuditLogWriter adminHeldPackageAuditLogWriter;
    private final SharedNotificationSender adminHeldPackageNotificationSender;

    /**
     * What it does: Connects the held-package service to the database, session, audit and notification services.
     * Input: The database client, package, assignment and account repositories, and shared services.
     * Output: The service for the held-package list, worker workload and reassignment.
     */
    public AdminHeldPackageService(JdbcClient adminHeldPackageDatabase,
            DeliveryPackageRepository adminHeldPackageRepository,
            DeliveryAssignmentHistoryRepository adminHeldPackageAssignmentRepository,
            AppUserRepository adminHeldPackageUserRepository, SharedCurrentUserReader adminHeldPackageCurrentUserReader,
            SharedAuditLogWriter adminHeldPackageAuditLogWriter,
            SharedNotificationSender adminHeldPackageNotificationSender) {
        this.adminHeldPackageDatabase = adminHeldPackageDatabase;
        this.adminHeldPackageRepository = adminHeldPackageRepository;
        this.adminHeldPackageAssignmentRepository = adminHeldPackageAssignmentRepository;
        this.adminHeldPackageUserRepository = adminHeldPackageUserRepository;
        this.adminHeldPackageCurrentUserReader = adminHeldPackageCurrentUserReader;
        this.adminHeldPackageAuditLogWriter = adminHeldPackageAuditLogWriter;
        this.adminHeldPackageNotificationSender = adminHeldPackageNotificationSender;
    }

    // ===== SECTION 3: adminSearchHeldPackage =====

    /**
     * What it does: Lists held packages and their responsible workers in order-reference order.
     * Input: The requested one-based page number.
     * Output: The selected rows and the paging totals from the same database snapshot.
     * Placeholder: PI-08 reads Warehouse and Delivery's saved custody and order rows.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchHeldPackageResponse adminSearchHeldPackage(int adminSearchHeldPackagePageNumber) {
        long adminSearchHeldPackageTotalRowCount = adminReadHeldPackageQuery("SELECT COUNT(*) FROM held")
                .query(Long.class).single();
        int adminSearchHeldPackageTotalPageCount = adminCountPage(adminSearchHeldPackageTotalRowCount);
        int adminSearchHeldPackageActualPageNumber = Math.clamp(
                adminSearchHeldPackagePageNumber, 1, adminSearchHeldPackageTotalPageCount);
        List<AdminSearchHeldPackageRow> adminSearchHeldPackageRowList = adminReadHeldPackageQuery("""
                SELECT * FROM held ORDER BY order_reference, package_id LIMIT :pageSize OFFSET :pageOffset
                """).param("pageOffset", (long) (adminSearchHeldPackageActualPageNumber - 1) * ADMIN_TABLE_PAGE_SIZE)
                .param("pageSize", ADMIN_TABLE_PAGE_SIZE).query(this::adminReadHeldPackageRow).list();
        return new AdminSearchHeldPackageResponse(adminSearchHeldPackageRowList, adminSearchHeldPackageActualPageNumber,
                adminSearchHeldPackageTotalPageCount, adminSearchHeldPackageTotalRowCount);
    }

    // ===== SECTION 4: adminViewDeliveryWorkerWorkload =====

    /**
     * What it does: Counts what each active Delivery Worker holds and what they are responsible for independently.
     * Input: None.
     * Output: Active Delivery Workers, fewest held packages first and then by username.
     * Placeholder: PI-08 reads the packages, custody and orders managed by Delivery and Warehouse.
     */
    @Transactional(readOnly = true)
    public AdminViewDeliveryWorkerWorkloadResponse adminViewDeliveryWorkerWorkload() {
        List<AdminViewDeliveryWorkerWorkloadRow> adminViewDeliveryWorkerWorkloadRowList = adminReadHeldPackageQuery("""
                , physical_workload AS (
                    SELECT current_handler_user_id, COUNT(*) AS held_count FROM held GROUP BY current_handler_user_id
                ), assigned_workload AS (
                    SELECT responsible_user_id, COUNT(*) AS responsible_count FROM held GROUP BY responsible_user_id
                )
                SELECT worker.user_id, worker.username, COALESCE(physical.held_count, 0) AS held_count,
                       COALESCE(assigned.responsible_count, 0) AS responsible_count
                FROM app_user worker
                LEFT JOIN physical_workload physical ON physical.current_handler_user_id = worker.user_id
                LEFT JOIN assigned_workload assigned ON assigned.responsible_user_id = worker.user_id
                WHERE worker.role = :deliveryWorkerRole AND worker.account_status = :activeAccountStatus
                ORDER BY held_count, worker.username
                """).param("activeAccountStatus", SharedAccountStatus.ACTIVE.name())
                .query((adminViewDeliveryWorkerWorkloadResult, adminViewDeliveryWorkerWorkloadRowIndex) ->
                        new AdminViewDeliveryWorkerWorkloadRow(adminViewDeliveryWorkerWorkloadResult.getLong("user_id"),
                                adminViewDeliveryWorkerWorkloadResult.getString("username"),
                                adminViewDeliveryWorkerWorkloadResult.getLong("held_count"),
                                adminViewDeliveryWorkerWorkloadResult.getLong("responsible_count")))
                .list();
        return new AdminViewDeliveryWorkerWorkloadResponse(adminViewDeliveryWorkerWorkloadRowList);
    }

    // ===== SECTION 5: adminReassignDelivery =====

    /**
     * What it does: Records one change of delivery responsibility and tells both workers.
     * Input: The package ID and the request with the replacement worker ID and reason.
     * Output: The saved decision, or an error for a missing package, invalid input or state conflict.
     * Placeholder: PO-05 gives Delivery and Warehouse the new responsibility; PO-08 gives them the notifications.
     */
    @Transactional
    public AdminDecidedRowResponse adminReassignDelivery(Long packageID,
            AdminReassignDeliveryRequest adminReassignDeliveryRequest) {
        String adminReassignDeliveryReason =
                adminCheckReasonText(adminReassignDeliveryRequest.adminReassignDeliveryReason());
        adminLockHeldPackage(packageID);
        AdminSearchHeldPackageRow adminReassignDeliveryPackage = adminReadHeldPackageQuery("""
                SELECT * FROM held WHERE package_id = :packageID
                """).param("packageID", packageID).query(this::adminReadHeldPackageRow).optional()
                .orElseThrow(() -> new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_PACKAGE_STATE_CONFLICT",
                        "Only a package that a Delivery Worker holds can be reassigned."));

        // Validate the worker inside the transaction so an account change cannot invalidate the assignment.
        Long adminReassignDeliveryNewWorkerUserID = adminReassignDeliveryRequest.adminReassignDeliveryNewWorkerUserID();
        AppUser adminReassignDeliveryNewWorker =
                adminReadDeliveryReplacementWorker(adminReassignDeliveryNewWorkerUserID);
        Long previousWorkerUserID = adminReassignDeliveryPackage.adminSearchHeldPackageResponsibleWorkerUserID();
        if (adminReassignDeliveryNewWorkerUserID.equals(previousWorkerUserID)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_WORKER_NOT_ALLOWED",
                    "This worker is already responsible for the delivery.");
        }

        SharedCurrentUser adminReassignDeliveryAdmin = adminHeldPackageCurrentUserReader.sharedReadCurrentUser();
        Instant assignedTime = adminSaveDeliveryReassignment(packageID, previousWorkerUserID,
                adminReassignDeliveryNewWorkerUserID, adminReassignDeliveryAdmin.userID(), adminReassignDeliveryReason);
        adminRecordDeliveryReassignment(adminReassignDeliveryPackage, adminReassignDeliveryAdmin.userID(),
                adminReassignDeliveryNewWorker, adminReassignDeliveryReason);
        String orderReference = adminReassignDeliveryPackage.adminSearchHeldPackageOrderReference();
        String username = adminReassignDeliveryNewWorker.username;
        String adminReassignDeliveryMessage = "Delivery of order " + orderReference + " is now the responsibility of "
                + username + ". The package changes hands only after Warehouse confirms its return and "
                + username + " collects it.";
        return new AdminDecidedRowResponse(adminReassignDeliveryMessage, null, username,
                adminReassignDeliveryAdmin.username(), assignedTime, null, false);
    }

    // ===== SECTION 6: HELPER FUNCTIONS =====

    /**
     * What it does: Prepares a query on the packages meeting the held-work definition of F2.5.
     * Input: The statement that consumes the held-package rows.
     * Output: A database statement with the held-package conditions bound.
     * Placeholder: PI-08 reads custody and order states from the shared tables.
     */
    private JdbcClient.StatementSpec adminReadHeldPackageQuery(String adminReadHeldPackageQueryStatement) {
        // PLACEHOLDER INPUT PI-08
        // From: Delivery and Warehouse.
        // What: Package custody, the holder and the order state.
        // How to use: Read the shared rows saved by those roles. Test tools supply them until integration.
        return adminHeldPackageDatabase.sql(ADMIN_HELD_PACKAGE_QUERY + adminReadHeldPackageQueryStatement)
                .param("warehouseCustodyStatus", SHARED_CUSTODY_STATUS_WAREHOUSE)
                .param("heldOrderStatusList", List.of(SharedOrderStatus.SHIPPED.name(),
                        SharedOrderStatus.OUT_FOR_DELIVERY.name(), SharedOrderStatus.DELIVERY_FAILED.name()))
                .param("deliveryWorkerRole", SharedUserRole.DELIVERY_WORKER.name());
    }

    /**
     * What it does: Maps one held package and its current responsibility for the Admin page.
     * Input: The database result and current row index.
     * Output: The held-package response row.
     */
    private AdminSearchHeldPackageRow adminReadHeldPackageRow(ResultSet adminReadHeldPackageRowResult,
            int adminReadHeldPackageRowIndex) throws SQLException {
        OffsetDateTime assignedTime = adminReadHeldPackageRowResult.getObject("assigned_at", OffsetDateTime.class);
        return new AdminSearchHeldPackageRow(adminReadHeldPackageRowResult.getLong("package_id"),
                adminReadHeldPackageRowResult.getString("package_qr_code"),
                adminReadHeldPackageRowResult.getLong("order_id"),
                adminReadHeldPackageRowResult.getString("order_reference"),
                adminReadHeldPackageRowResult.getString("order_status"),
                adminReadHeldPackageRowResult.getLong("current_handler_user_id"),
                adminReadHeldPackageRowResult.getString("handler_username"),
                SharedAccountStatus.valueOf(adminReadHeldPackageRowResult.getString("handler_account_status")),
                adminReadHeldPackageRowResult.getLong("responsible_user_id"),
                adminReadHeldPackageRowResult.getString("responsible_username"),
                assignedTime == null ? null : assignedTime.toInstant());
    }

    /**
     * What it does: Locks the selected package until the reassignment transaction ends.
     * Input: The package ID.
     * Output: No value. Throws a not-found error or an immediate state conflict if already locked.
     */
    private void adminLockHeldPackage(Long packageID) {
        try {
            adminHeldPackageRepository.findByPackageIDForUpdateNowait(packageID).orElseThrow(() ->
                    new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_PACKAGE_NOT_FOUND", "Package not found."));
        } catch (PessimisticLockingFailureException adminLockHeldPackageError) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_PACKAGE_STATE_CONFLICT",
                    "Another change is in progress for this package. Please refresh the page.");
        }
    }

    /**
     * What it does: Locks and checks the replacement worker's account.
     * Input: The requested Delivery Worker account ID.
     * Output: The active Delivery Worker, or a worker-not-allowed conflict.
     */
    private AppUser adminReadDeliveryReplacementWorker(Long userID) {
        AppUser adminReadDeliveryReplacementWorkerAccount = userID == null ? null
                : adminHeldPackageUserRepository.findByUserIDForChange(userID).orElse(null);
        if (adminReadDeliveryReplacementWorkerAccount == null
                || adminReadDeliveryReplacementWorkerAccount.role != SharedUserRole.DELIVERY_WORKER
                || adminReadDeliveryReplacementWorkerAccount.accountStatus != SharedAccountStatus.ACTIVE) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_WORKER_NOT_ALLOWED",
                    "Choose an active Delivery Worker.");
        }
        return adminReadDeliveryReplacementWorkerAccount;
    }

    /**
     * What it does: Saves a delivery responsibility change for the locked package.
     * Input: Package, previous worker, new worker and deciding Admin IDs and the reason.
     * Output: The saved assignment time.
     * Placeholder: PO-05 is the history record that Delivery and Warehouse use to route the package.
     */
    private Instant adminSaveDeliveryReassignment(Long packageID, Long previousWorkerUserID, Long newWorkerUserID,
            Long assignedByAdminID, String reason) {
        // PLACEHOLDER OUTPUT PO-05
        // To: Delivery and Warehouse.
        // What: One saved responsibility change while the original worker holds the package.
        // How to use: The former holder returns the package to Warehouse and the new worker collects it after receipt.
        DeliveryAssignmentHistory adminSaveDeliveryReassignmentRow = new DeliveryAssignmentHistory();
        adminSaveDeliveryReassignmentRow.packageID = packageID;
        adminSaveDeliveryReassignmentRow.previousWorkerUserID = previousWorkerUserID;
        adminSaveDeliveryReassignmentRow.newWorkerUserID = newWorkerUserID;
        adminSaveDeliveryReassignmentRow.assignedByAdminID = assignedByAdminID;
        adminSaveDeliveryReassignmentRow.reason = reason;
        adminSaveDeliveryReassignmentRow.assignedTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        adminHeldPackageAssignmentRepository.save(adminSaveDeliveryReassignmentRow);
        return adminSaveDeliveryReassignmentRow.assignedTime;
    }

    /**
     * What it does: Audits the old and new responsibility and sends each worker their notification.
     * Input: The package and previous responsible worker, the Admin ID, the new worker and the reason.
     * Output: One audit entry and two notifications in the caller's transaction.
     * Placeholder: PO-08 makes both notifications available to Delivery.
     */
    private void adminRecordDeliveryReassignment(AdminSearchHeldPackageRow adminRecordDeliveryReassignmentPackage,
            Long actorUserID, AppUser adminRecordDeliveryReassignmentNewWorker, String reason) {
        String orderReference = adminRecordDeliveryReassignmentPackage.adminSearchHeldPackageOrderReference();
        Long previousWorkerUserID =
                adminRecordDeliveryReassignmentPackage.adminSearchHeldPackageResponsibleWorkerUserID();
        adminHeldPackageAuditLogWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_DELIVERY_REASSIGNED,
                "PACKAGE", adminRecordDeliveryReassignmentPackage.packageID().toString(),
                Map.of("workerUserID", previousWorkerUserID, "username",
                        adminRecordDeliveryReassignmentPackage.adminSearchHeldPackageResponsibleWorkerUsername()),
                Map.of("workerUserID", adminRecordDeliveryReassignmentNewWorker.userID,
                        "username", adminRecordDeliveryReassignmentNewWorker.username,
                        "reason", reason, "orderReference", orderReference));

        // PLACEHOLDER OUTPUT PO-08
        // To: Delivery.
        // What: Unread messages about removed and assigned responsibility, one per worker.
        // How to use: Show each message to its recipient and allow marking it read.
        Long orderID = adminRecordDeliveryReassignmentPackage.orderID();
        SharedNotificationType adminRecordDeliveryReassignmentRemovedType =
                SharedNotificationType.WORKER_DELIVERY_REMOVED;
        adminHeldPackageNotificationSender.sharedSendNotification(previousWorkerUserID, orderID,
                adminRecordDeliveryReassignmentRemovedType,
                adminRecordDeliveryReassignmentRemovedType.sharedFormatNotificationMessage(orderReference));
        SharedNotificationType adminRecordDeliveryReassignmentAssignedType =
                SharedNotificationType.WORKER_DELIVERY_ASSIGNED;
        adminHeldPackageNotificationSender.sharedSendNotification(adminRecordDeliveryReassignmentNewWorker.userID,
                orderID, adminRecordDeliveryReassignmentAssignedType,
                adminRecordDeliveryReassignmentAssignedType.sharedFormatNotificationMessage(orderReference));
    }
}
