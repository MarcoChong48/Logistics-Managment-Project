// TEMPORARY TEST CODE - remove before integration
// Local fixtures exercise accounts, sessions, active work, logs, stock and orders.
package com.lms.backend.admin.temporarytest;

import static com.lms.backend.admin.user.AdminUserRule.adminCheckSuperAdminRule;
import static com.lms.backend.shared.login.SharedLoginRule.SHARED_TEMPORARY_PASSWORD_VALID_HOURS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_ATTEMPT_OUTCOME_DELIVERED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_ATTEMPT_OUTCOME_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_ATTEMPT_OUTCOME_IN_PROGRESS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_COLLECTION_OUTCOME_COLLECTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_COLLECTION_OUTCOME_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_EVENT_PICKED_UP;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_EVENT_RETURN_RECEIVED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_EVENT_RETURN_REQUESTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_DELIVERY;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_WAREHOUSE;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_FAILURE_REASON_CLIENT_NOT_HOME;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_STATUS_APPROVED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_TYPE_REDELIVER;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_ACCEPTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_IN_PROGRESS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PACKED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKUP_LIST_STATUS_CLOSED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKUP_LIST_STATUS_OPEN;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_REQUESTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_CONSUMED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_HELD;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_RELEASED;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.admin.log.AdminLogCleanupTask;
import com.lms.backend.admin.placeholder.AdminShortageInputPlaceholder;
import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.database.PickingScanHistoryRepository;
import com.lms.backend.shared.error.SharedMessageResponse;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedSecurityEvent;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.login.SharedPasswordRule;
import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedInstructionSource;
import com.lms.backend.shared.value.SharedOrderStatus;
import com.lms.backend.shared.value.SharedProofType;
import com.lms.backend.shared.value.SharedScanResult;
import com.lms.backend.shared.value.SharedUserRole;

@Service
@Profile("admintest")
public class AdminTemporaryTestToolService {

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 creates low-stock and reservation-shortfall fixtures without a Warehouse page.
    /**
     * What it does: Sets both stock numbers on an existing test-database product.
     * Input: The product SKU and two nonnegative whole numbers.
     * Output: A shared message or an error for invalid input or missing stock.
     */
    @Transactional
    public SharedMessageResponse adminTemporarySetProductStock(
            AdminTemporarySetProductStockRequest adminTemporarySetProductStockRequest) {
        String adminTemporarySetProductStockSKU = adminTemporarySetProductStockRequest.adminTemporarySetProductStockSKU();
        BigDecimal adminTemporarySetProductStockPhysicalStock =
                adminTemporarySetProductStockRequest.adminTemporarySetProductStockPhysicalStock();
        BigDecimal adminTemporarySetProductStockReservedStock =
                adminTemporarySetProductStockRequest.adminTemporarySetProductStockReservedStock();
        if (adminTemporarySetProductStockSKU == null || adminTemporarySetProductStockSKU.isBlank()
                || adminTemporarySetProductStockPhysicalStock == null || adminTemporarySetProductStockReservedStock == null) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID",
                    "Enter a SKU and both stock numbers.");
        }

        // The database stores nonnegative integers and allows a reservation shortfall.
        int physicalStock;
        int reservedStock;
        try {
            physicalStock = adminTemporarySetProductStockPhysicalStock.intValueExact();
            reservedStock = adminTemporarySetProductStockReservedStock.intValueExact();
        } catch (ArithmeticException adminTemporarySetProductStockNumberError) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID",
                    "Stock numbers must be whole numbers that fit in the database.");
        }
        if (physicalStock < 0 || reservedStock < 0) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID",
                    "Stock numbers cannot be negative.");
        }

        // One update saves both numbers together, including when reserved exceeds physical.
        String SKU = adminTemporarySetProductStockSKU.strip().toUpperCase(Locale.ROOT);
        int adminTemporarySetProductStockUpdatedCount = adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE inventory
                SET physical_stock = :physicalStock, reserved_stock = :reservedStock, updated_at = CURRENT_TIMESTAMP
                FROM product
                WHERE inventory.product_id = product.product_id AND product.sku = :SKU
                """)
                .param("physicalStock", physicalStock)
                .param("reservedStock", reservedStock)
                .param("SKU", SKU)
                .update();
        if (adminTemporarySetProductStockUpdatedCount == 0) {
            throw new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_PRODUCT_NOT_FOUND",
                    "No product stock was found for this SKU.");
        }
        return new SharedMessageResponse("Stock for " + SKU + " was set to " + physicalStock
                + " physical and " + reservedStock + " reserved.");
    }

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final AppUserRepository adminTemporaryTestUserRepository;
    private final JdbcClient adminTemporaryCreateTestUserDatabase;
    private final PasswordEncoder adminTemporaryCreateTestUserPasswordEncoder;
    private final SharedSecurityLogWriter adminTemporaryCreateTestUserLogWriter;
    private final TransactionTemplate adminTemporaryCreateTestUserTransaction;
    private final AdminLogCleanupTask adminTemporaryRunLogCleanupTask;
    private final PickingScanHistoryRepository adminTemporaryCreateOldLogRowPickingScanRepository;
    private final String adminTemporarySetUserStatusBootstrapUsername;
    private final SharedCurrentUserReader adminTemporaryCreateSampleOrderSetCurrentUserReader;
    private final AdminShortageInputPlaceholder adminTemporaryCreateShortageReportInput;

    /**
     * What it does: Receives the database and password helpers for the local test tools.
     * Input: Database and password helpers, the log writer, cleanup task, bootstrap Admin name and current user reader.
     * Output: A test tools service that only loads in test mode.
     */
    public AdminTemporaryTestToolService(AppUserRepository adminTemporaryTestUserRepository,
            JdbcClient adminTemporaryCreateTestUserDatabase,
            PasswordEncoder adminTemporaryCreateTestUserPasswordEncoder,
            SharedSecurityLogWriter adminTemporaryCreateTestUserLogWriter,
            PlatformTransactionManager adminTemporaryCreateTestUserTransactionManager,
            AdminLogCleanupTask adminTemporaryRunLogCleanupTask,
            PickingScanHistoryRepository adminTemporaryCreateOldLogRowPickingScanRepository,
            SharedCurrentUserReader adminTemporaryCreateSampleOrderSetCurrentUserReader,
            AdminShortageInputPlaceholder adminTemporaryCreateShortageReportInput,
            @Value("${lms.shared.bootstrap-admin-username:}") String adminTemporarySetUserStatusBootstrapUsername) {
        this.adminTemporaryTestUserRepository = adminTemporaryTestUserRepository;
        this.adminTemporaryCreateTestUserDatabase = adminTemporaryCreateTestUserDatabase;
        this.adminTemporaryCreateTestUserPasswordEncoder = adminTemporaryCreateTestUserPasswordEncoder;
        this.adminTemporaryCreateTestUserLogWriter = adminTemporaryCreateTestUserLogWriter;
        this.adminTemporaryCreateTestUserTransaction =
                new TransactionTemplate(adminTemporaryCreateTestUserTransactionManager);
        this.adminTemporaryRunLogCleanupTask = adminTemporaryRunLogCleanupTask;
        this.adminTemporaryCreateOldLogRowPickingScanRepository = adminTemporaryCreateOldLogRowPickingScanRepository;
        this.adminTemporarySetUserStatusBootstrapUsername = adminTemporarySetUserStatusBootstrapUsername;
        this.adminTemporaryCreateSampleOrderSetCurrentUserReader = adminTemporaryCreateSampleOrderSetCurrentUserReader;
        this.adminTemporaryCreateShortageReportInput = adminTemporaryCreateShortageReportInput;
    }

    // ===== SECTION 2: adminTemporaryViewTestMode =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 0 confirms that the test profile loaded this service.
    /**
     * What it does: Reports that test mode is active.
     * Input: None.
     * Output: The test-mode record with its flag set to true.
     */
    public AdminTemporaryViewTestModeResponse adminTemporaryViewTestMode() {
        // Spring creates this service only when the test profile is active.
        return new AdminTemporaryViewTestModeResponse(true);
    }

    // ===== SECTION 3: adminTemporaryCreateTestUser =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 needs workers and clients with initial passwords to test shared account rules.
    /**
     * What it does: Creates an active test account and issues a temporary password.
     * Input: The chosen test account role.
     * Output: A message with the test username and the password just issued.
     */
    public SharedMessageResponse adminTemporaryCreateTestUser(
            AdminTemporaryCreateTestUserRequest adminTemporaryCreateTestUserRequest) {
        // Validate the role and create the account before writing its separate security event.
        SharedUserRole role = adminTemporaryCreateTestUserRequest.adminTemporaryCreateTestUserRole();
        if (role == null) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID", "Choose a role.");
        }
        String adminTemporaryCreateTestUserPassword = SharedPasswordRule.sharedCreateTemporaryPassword();
        Long userID = adminTemporaryCreateTestUserTransaction.execute(adminTemporaryCreateTestUserTransactionStatus ->
                adminTemporaryCreateTestUser(role, adminTemporaryCreateTestUserPassword));
        adminTemporaryCreateTestUserLogWriter.sharedWriteSecurityLog(userID,
                SharedSecurityEvent.SHARED_SECURITY_EVENT_TEMPORARY_PASSWORD_ISSUED, "SUCCESS", "test user");

        // Read the issued account name. The password is returned only in this tool answer.
        String username = adminTemporaryCreateTestUserDatabase.sql("SELECT username FROM app_user WHERE user_id = :id")
                .param("id", userID).query(String.class).single();
        return new SharedMessageResponse("Created " + username + ". Temporary password: "
                + adminTemporaryCreateTestUserPassword);
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 2 reuses the test-account insert inside the sample order transaction.
    /**
     * What it does: Inserts a test account with an unused numbered username and a password hash.
     * Input: The test role and temporary password. The caller starts the transaction.
     * Output: The ID of the inserted account.
     */
    private Long adminTemporaryCreateTestUser(SharedUserRole role, String adminTemporaryCreateTestUserPassword) {
        // Each role has a recognisable part in a generated test username.
        String adminTemporaryCreateTestUserRoleWord = switch (role) {
            case CLIENT -> "client";
            case WAREHOUSE_WORKER -> "warehouse";
            case DELIVERY_WORKER -> "delivery";
            case ADMIN -> "admin";
        };
        String adminTemporaryCreateTestUserUsernamePrefix = "test_" + adminTemporaryCreateTestUserRoleWord + "_";

        // Allocate the username suffix from the account sequence and save only the password hash.
        String passwordHash = adminTemporaryCreateTestUserPasswordEncoder.encode(adminTemporaryCreateTestUserPassword);
        Instant temporaryPasswordExpiryTime =
                Instant.now().plus(SHARED_TEMPORARY_PASSWORD_VALID_HOURS, ChronoUnit.HOURS);
        Optional<Long> adminTemporaryCreateTestUserInsertedID;
        do {
            // If the generated username is already taken, take the next sequence number.
            adminTemporaryCreateTestUserInsertedID = adminTemporaryCreateTestUserDatabase.sql("""
                    WITH next_user AS (
                        SELECT nextval(pg_get_serial_sequence('app_user', 'user_id')) AS user_id
                    )
                    INSERT INTO app_user
                        (user_id, username, password_hash, role, must_change_password, temp_password_expires_at)
                    SELECT user_id, :usernamePrefix || user_id, :passwordHash, :role, true, :expiry
                    FROM next_user
                    ON CONFLICT DO NOTHING
                    RETURNING user_id
                    """)
                    .param("usernamePrefix", adminTemporaryCreateTestUserUsernamePrefix)
                    .param("passwordHash", passwordHash)
                    .param("role", role.name())
                    .param("expiry", temporaryPasswordExpiryTime.atOffset(ZoneOffset.UTC))
                    .query(Long.class)
                    .optional();
        } while (adminTemporaryCreateTestUserInsertedID.isEmpty());
        return adminTemporaryCreateTestUserInsertedID.orElseThrow();
    }

    // ===== SECTION 4: adminTemporaryEndUserLock =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 exercises automatic unlock without making the tester wait.
    /**
     * What it does: Moves a test account's lock time into the past.
     * Input: The account's username.
     * Output: A message confirming the lock time was moved, or a shared input error.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryEndUserLock(
            AdminTemporaryEndUserLockRequest adminTemporaryEndUserLockRequest) {
        AppUser adminTemporaryEndUserLockUser = adminTemporaryFindTestUser(
                adminTemporaryEndUserLockRequest.adminTemporaryEndUserLockUsername());
        adminTemporaryEndUserLockUser.lockUntil = Instant.now().minus(1, ChronoUnit.MINUTES);
        return new SharedMessageResponse("Ended the lock for " + adminTemporaryEndUserLockUser.username + ".");
    }

    // ===== SECTION 5: adminTemporaryExpireTemporaryPassword =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 needs to exercise a temporary password after expiry.
    /**
     * What it does: Moves the temporary password's expiry time into the past.
     * Input: The account's username.
     * Output: A confirmation message, a shared input error, or 409 for the protected super admin.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryExpireTemporaryPassword(
            AdminTemporaryExpireTemporaryPasswordRequest adminTemporaryExpireTemporaryPasswordRequest) {
        AppUser adminTemporaryExpireTemporaryPasswordUser = adminTemporaryFindTestUser(
                adminTemporaryExpireTemporaryPasswordRequest.adminTemporaryExpireTemporaryPasswordUsername());
        // Keep the backup Admin's password available while testing other accounts.
        if (adminCheckSuperAdminRule(adminTemporaryExpireTemporaryPasswordUser.username,
                adminTemporaryExpireTemporaryPasswordUser.role, adminTemporarySetUserStatusBootstrapUsername)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_SUPER_ADMIN_PROTECTED",
                    "The super admin account cannot be changed.");
        }
        adminTemporaryExpireTemporaryPasswordUser.temporaryPasswordExpiryTime = Instant.now()
                .minus(1, ChronoUnit.MINUTES);
        return new SharedMessageResponse("Expired the temporary password for "
                + adminTemporaryExpireTemporaryPasswordUser.username + ".");
    }

    // ===== SECTION 6: adminTemporarySetUserStatus =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 exercises revocation when the account is no longer active.
    /**
     * What it does: Sets the specified status on an account.
     * Input: The username and requested account status.
     * Output: A message with the saved status, a shared input error, or 409 for the protected super admin.
     */
    @Transactional
    public SharedMessageResponse adminTemporarySetUserStatus(
            AdminTemporarySetUserStatusRequest adminTemporarySetUserStatusRequest) {
        var accountStatus = adminTemporarySetUserStatusRequest.adminTemporarySetUserStatusAccountStatus();
        if (accountStatus == null) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID", "Choose an account status.");
        }

        AppUser adminTemporarySetUserStatusUser = adminTemporaryFindTestUser(
                adminTemporarySetUserStatusRequest.adminTemporarySetUserStatusUsername());
        // The backup Admin stays available regardless of the requested test status.
        if (adminCheckSuperAdminRule(adminTemporarySetUserStatusUser.username, adminTemporarySetUserStatusUser.role,
                adminTemporarySetUserStatusBootstrapUsername)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_SUPER_ADMIN_PROTECTED",
                    "The super admin account cannot be changed.");
        }
        adminTemporarySetUserStatusUser.accountStatus = accountStatus;
        return new SharedMessageResponse("Set the status of " + adminTemporarySetUserStatusUser.username + " to "
                + accountStatus + ".");
    }

    // ===== SECTION 7: adminTemporaryCreateManyTestUser =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 2 needs predictable usernames for filters and enough rows for multiple pages.
    /**
     * What it does: Fills the numbered range of Client test accounts without replacing existing rows.
     * Input: None.
     * Output: A shared message with the range and number of new accounts.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryCreateManyTestUser() {
        // Keep the same names and credentials if the fixture is requested again.
        var adminTemporaryCreateManyTestUserMissingUsernameList = adminTemporaryCreateTestUserDatabase.sql("""
                SELECT 'test_client_' || number
                FROM generate_series(1, 25) AS number
                WHERE NOT EXISTS (
                    SELECT 1 FROM app_user WHERE lower(username) = 'test_client_' || number
                )
                ORDER BY number
                """).query(String.class).list();
        int adminTemporaryCreateManyTestUserCreatedCount = 0;

        // Issue separate temporary passwords and avoid clobbering any concurrent insert.
        for (String username : adminTemporaryCreateManyTestUserMissingUsernameList) {
            String passwordHash = adminTemporaryCreateTestUserPasswordEncoder.encode(
                    SharedPasswordRule.sharedCreateTemporaryPassword());
            adminTemporaryCreateManyTestUserCreatedCount += adminTemporaryCreateTestUserDatabase.sql("""
                    INSERT INTO app_user
                        (username, password_hash, role, must_change_password, temp_password_expires_at)
                    VALUES (:username, :passwordHash, :role, true, NOW() + INTERVAL '24 hours')
                    ON CONFLICT DO NOTHING
                    """)
                    .param("username", username)
                    .param("passwordHash", passwordHash)
                    .param("role", SharedUserRole.CLIENT.name())
                    .update();
        }

        return new SharedMessageResponse("Test users test_client_1 through test_client_25 are ready. New accounts: "
                + adminTemporaryCreateManyTestUserCreatedCount + ".");
    }

    // ===== SECTION 8: adminTemporaryCreateWorkerWithActiveWork =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 2 needs both assigned picking tasks and separate open-list and package custody.
    /**
     * What it does: Creates two test workers with four sample orders and saves them together.
     * Input: None.
     * Output: Both test usernames, temporary passwords and their order references.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryCreateWorkerWithActiveWork() {
        // Reuse the same client and workers across the sample orders in this fixture.
        String adminTemporaryCreateWorkerWithActiveWorkWarehousePassword =
                SharedPasswordRule.sharedCreateTemporaryPassword();
        String adminTemporaryCreateWorkerWithActiveWorkDeliveryPassword =
                SharedPasswordRule.sharedCreateTemporaryPassword();
        Long warehouseWorkerUserID = adminTemporaryCreateTestUser(SharedUserRole.WAREHOUSE_WORKER,
                adminTemporaryCreateWorkerWithActiveWorkWarehousePassword);
        Long deliveryWorkerUserID = adminTemporaryCreateTestUser(SharedUserRole.DELIVERY_WORKER,
                adminTemporaryCreateWorkerWithActiveWorkDeliveryPassword);
        Long clientUserID = adminTemporaryCreateTestUser(SharedUserRole.CLIENT,
                SharedPasswordRule.sharedCreateTemporaryPassword());
        String adminTemporaryCreateWorkerWithActiveWorkFirstPickingReference = adminTemporaryCreateSampleOrder(
                SharedOrderStatus.PICKING, clientUserID, warehouseWorkerUserID, null);
        String adminTemporaryCreateWorkerWithActiveWorkSecondPickingReference = adminTemporaryCreateSampleOrder(
                SharedOrderStatus.PICKING, clientUserID, warehouseWorkerUserID, null);
        String adminTemporaryCreateWorkerWithActiveWorkShippedReference = adminTemporaryCreateSampleOrder(
                SharedOrderStatus.SHIPPED, clientUserID, warehouseWorkerUserID, deliveryWorkerUserID);
        String adminTemporaryCreateWorkerWithActiveWorkReadyForPickupReference = adminTemporaryCreateSampleOrder(
                SharedOrderStatus.READY_FOR_PICKUP, clientUserID, warehouseWorkerUserID, null);

        // Claim a separate list without handing its package to the Delivery Worker.
        Long pickupListID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO pickup_list
                    (list_qr_code, created_by_worker_id, status, claimed_by_delivery_user_id, claimed_at)
                VALUES (:listQRCode, :warehouseWorkerUserID, :status, :deliveryWorkerUserID, NOW())
                RETURNING pickup_list_id
                """)
                .param("listQRCode", adminTemporaryCreateWorkerWithActiveWorkReadyForPickupReference + "-LIST")
                .param("warehouseWorkerUserID", warehouseWorkerUserID)
                .param("status", SHARED_PICKUP_LIST_STATUS_OPEN)
                .param("deliveryWorkerUserID", deliveryWorkerUserID)
                .query(Long.class).single();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO pickup_list_item (pickup_list_id, package_id, collection_outcome)
                SELECT :pickupListID, package.package_id, :collectionOutcome
                FROM delivery_package package
                JOIN customer_order orders ON orders.order_id = package.order_id
                WHERE orders.order_reference = :orderReference
                """)
                .param("pickupListID", pickupListID)
                .param("collectionOutcome", SHARED_COLLECTION_OUTCOME_PENDING)
                .param("orderReference", adminTemporaryCreateWorkerWithActiveWorkReadyForPickupReference)
                .update();

        // Separate each password from the next label with a space for clean copying.
        return new SharedMessageResponse("Warehouse Worker: test_warehouse_" + warehouseWorkerUserID
                + ". Temporary password: " + adminTemporaryCreateWorkerWithActiveWorkWarehousePassword
                + " | Picking orders: " + adminTemporaryCreateWorkerWithActiveWorkFirstPickingReference
                + ", " + adminTemporaryCreateWorkerWithActiveWorkSecondPickingReference
                + " | Delivery Worker: test_delivery_" + deliveryWorkerUserID
                + ". Temporary password: " + adminTemporaryCreateWorkerWithActiveWorkDeliveryPassword
                + " | Held package order: " + adminTemporaryCreateWorkerWithActiveWorkShippedReference
                + " | Open pickup list order: " + adminTemporaryCreateWorkerWithActiveWorkReadyForPickupReference + ".");
    }

    // ===== SECTION 9: adminTemporaryCreatePickingScanRow =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 3 uses accepted, reversed and rejected scans to verify read-only history and exports.
    /**
     * What it does: Adds five scans to the builder's one accepted mug scan.
     * Input: None.
     * Output: A shared message with the order reference.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryCreatePickingScanRow() {
        String orderReference = adminTemporaryCreateSampleOrder(SharedOrderStatus.PICKING, null, null, null);

        // Add the accepted scans with their trusted item IDs, then refer to the new pen scan from its reversal.
        adminTemporaryCreateTestUserDatabase.sql("""
                WITH task AS (
                    SELECT picking.picking_task_id, assignment.worker_user_id, picking.order_id
                    FROM customer_order orders
                    JOIN picking_task picking ON picking.order_id = orders.order_id
                    JOIN picking_assignment_history assignment ON assignment.picking_task_id = picking.picking_task_id
                    WHERE orders.order_reference = :orderReference AND assignment.ended_at IS NULL
                ), accepted AS (
                    INSERT INTO picking_scan_history
                        (picking_task_id, worker_user_id, order_item_id, scanned_identifier, result)
                    SELECT task.picking_task_id, task.worker_user_id, item.order_item_id, product.sku, 'ACCEPTED'
                    FROM task JOIN order_item item ON item.order_id = task.order_id
                    JOIN product ON product.product_id = item.product_id
                    RETURNING picking_task_id, worker_user_id, order_item_id, scanned_identifier, scan_id
                )
                INSERT INTO picking_scan_history
                    (picking_task_id, worker_user_id, order_item_id, scanned_identifier, result, reason,
                     original_scan_id)
                SELECT picking_task_id, worker_user_id, order_item_id, scanned_identifier, 'REVERSED',
                       'picked by mistake', scan_id
                FROM accepted WHERE scanned_identifier LIKE 'TEST-PEN-%'
                UNION ALL
                SELECT picking_task_id, worker_user_id, NULL, 'PRIVATE-PHONE-MARKER', 'REJECTED',
                       'not part of this order, call 5550 0000 1234', NULL
                FROM task
                UNION ALL
                SELECT picking_task_id, worker_user_id, NULL, '=1+1', 'REJECTED', '=1+1', NULL FROM task
                """).param("orderReference", orderReference).update();

        // Keep the durable quantity consistent with the mug scans. The pen scan was reversed.
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE order_item item SET picked_quantity = 2
                FROM customer_order orders, product
                WHERE item.order_id = orders.order_id AND item.product_id = product.product_id
                  AND orders.order_reference = :orderReference AND product.sku LIKE 'TEST-MUG-%'
                """).param("orderReference", orderReference).update();
        return new SharedMessageResponse("Created picking scan rows for " + orderReference + ".");
    }

    // ===== SECTION 10: adminTemporaryCreateOldLogRow =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 3 checks repeatable fixtures and the retention rule for originals of newer reversals.
    /**
     * What it does: Replaces the tagged retention fixtures, using a synthetic picking task.
     * Input: None.
     * Output: A shared message confirming the inserted counts.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryCreateOldLogRow() {
        // Serialize fixture creation with another press or a scheduled cleanup.
        adminTemporaryCreateOldLogRowPickingScanRepository.adminLockLogCleanup();
        // Reuse the last fixture's picking task while its tagged rows still exist.
        String orderReference = adminTemporaryCreateTestUserDatabase.sql("""
                SELECT orders.order_reference
                FROM picking_scan_history scan
                JOIN picking_task task ON task.picking_task_id = scan.picking_task_id
                JOIN customer_order orders ON orders.order_id = task.order_id
                WHERE scan.reason = 'TEMPORARY-OLD-ROW'
                ORDER BY scan.scan_id DESC LIMIT 1
                """).query(String.class).optional().orElseGet(
                        () -> adminTemporaryCreateSampleOrder(SharedOrderStatus.PICKING, null, null, null));

        // Remove only the tagged fixtures, with reversals first to preserve the foreign key.
        adminTemporaryCreateTestUserDatabase.sql(
                "DELETE FROM security_log WHERE details = 'TEMPORARY-OLD-ROW'").update();
        adminTemporaryCreateTestUserDatabase.sql("""
                DELETE FROM picking_scan_history WHERE reason = 'TEMPORARY-OLD-ROW' AND original_scan_id IS NOT NULL
                """).update();
        adminTemporaryCreateTestUserDatabase.sql(
                "DELETE FROM picking_scan_history WHERE reason = 'TEMPORARY-OLD-ROW'").update();
        adminTemporaryCreateTestUserDatabase.sql("DELETE FROM audit_log WHERE action = 'TEMPORARY_OLD_ROW'").update();

        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO security_log (event_type, event_time, result, details)
                SELECT 'LOGIN_FAILURE', NOW() - age, 'FAILURE', 'TEMPORARY-OLD-ROW'
                FROM (VALUES (INTERVAL '91 days'), (INTERVAL '91 days'),
                             (INTERVAL '89 days'), (INTERVAL '89 days')) AS fixture(age)
                """).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO audit_log (action, target_type, target_id, event_time, result)
                VALUES ('TEMPORARY_OLD_ROW', 'LOG', 'LOG_CLEANUP', NOW() - INTERVAL '3 years 1 day', 'SUCCESS'),
                       ('TEMPORARY_OLD_ROW', 'LOG', 'LOG_CLEANUP', NOW() - INTERVAL '3 years' + INTERVAL '1 day',
                        'SUCCESS')
                """).update();
        adminTemporaryCreateOldLogRow(orderReference);
        return new SharedMessageResponse("Created 4 security log rows, 5 picking scan rows and 2 audit log rows.");
    }

    /**
     * What it does: Creates three aged accepted scans plus two reversals with different ages.
     * Input: The synthetic picking order reference.
     * Output: None. The caller owns the transaction.
     */
    private void adminTemporaryCreateOldLogRow(String orderReference) {
        // Carry each original's ID into the reversal. Only the 95-day scan has a kept reversal.
        adminTemporaryCreateTestUserDatabase.sql("""
                WITH task AS (
                    SELECT scan.picking_task_id, scan.worker_user_id, scan.order_item_id, scan.scanned_identifier
                    FROM picking_scan_history scan
                    JOIN picking_task picking ON picking.picking_task_id = scan.picking_task_id
                    JOIN customer_order orders ON orders.order_id = picking.order_id
                    WHERE orders.order_reference = :orderReference AND scan.result = 'ACCEPTED'
                    ORDER BY scan.scan_time DESC, scan.scan_id DESC LIMIT 1
                ), originals AS (
                    INSERT INTO picking_scan_history
                        (picking_task_id, worker_user_id, order_item_id, scanned_identifier, scan_time, result, reason)
                    SELECT task.*, NOW() - age, 'ACCEPTED', 'TEMPORARY-OLD-ROW'
                    FROM task CROSS JOIN (VALUES (INTERVAL '91 days'), (INTERVAL '95 days'),
                                                 (INTERVAL '89 days')) AS fixture(age)
                    RETURNING picking_task_id, worker_user_id, order_item_id, scanned_identifier, scan_time, scan_id
                )
                INSERT INTO picking_scan_history
                    (picking_task_id, worker_user_id, order_item_id, scanned_identifier, scan_time,
                     result, reason, original_scan_id)
                SELECT picking_task_id, worker_user_id, order_item_id, scanned_identifier,
                       CASE WHEN scan_time = NOW() - INTERVAL '91 days' THEN scan_time
                            ELSE NOW() - INTERVAL '10 days' END,
                       'REVERSED', 'TEMPORARY-OLD-ROW', scan_id
                FROM originals WHERE scan_time < NOW() - INTERVAL '90 days'
                """).param("orderReference", orderReference).update();
    }

    // ===== SECTION 11: adminTemporaryRunLogCleanup =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 3 runs the exact scheduled cleanup on demand.
    /**
     * What it does: Runs log cleanup now using its own transaction.
     * Input: None.
     * Output: A shared completion message or an error if cleanup failed.
     */
    public SharedMessageResponse adminTemporaryRunLogCleanup() {
        adminTemporaryRunLogCleanupTask.adminRunLogCleanup();
        return new SharedMessageResponse("Log clean-up finished.");
    }

    // ===== SECTION 12: adminTemporaryCreatePrivateValueLogRow =====

    // TEMPORARY TEST CODE - remove before integration
    // Only synthetic markers are inserted directly to exercise log filtering.
    /**
     * What it does: Inserts the synthetic private values specified for Stage 3.
     * Input: None.
     * Output: A shared completion message.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryCreatePrivateValueLogRow() {
        // Bypass ordinary log writers so the read-side privacy filter receives the raw synthetic values.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO audit_log (action, target_type, target_id, new_value, result)
                VALUES ('TEMPORARY_PRIVATE_VALUE_ROW', 'LOG', 'TEMPORARY_PRIVATE_VALUE_ROW',
                        jsonb_build_object('phone', 'PRIVATE-PHONE-MARKER', 'address', 'PRIVATE-ADDRESS-MARKER',
                                           'recipientName', 'PRIVATE-RECIPIENT-MARKER',
                                           'passwordHash', 'PRIVATE-PASSWORD-MARKER', 'note', 'call 5550 0000 1234'),
                        'SUCCESS')
                """).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO security_log (event_type, result, details)
                VALUES ('TEMPORARY_PRIVATE_VALUE_ROW', 'SUCCESS',
                        'call 5550 0000 1234 or 9123-4567 about order TEST-0001 on 2026-11-10')
                """).update();
        return new SharedMessageResponse(
                "Created one audit log row and one security log row with private test values.");
    }

    // ===== SECTION 13: adminTemporaryCreateSampleOrderSet =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 creates a complete set for Orders and its detail pages.
    /**
     * What it does: Creates one order in each of the ten statuses in the same transaction.
     * Input: None. The signed-in Admin is the resolver of the sample redelivery request.
     * Output: A shared message listing each new order reference and its status.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryCreateSampleOrderSet() {
        Long adminTemporaryCreateSampleOrderSetAdminUserID = adminTemporaryCreateSampleOrderSetCurrentUserReader
                .sharedReadCurrentUser().userID();
        StringJoiner adminTemporaryCreateSampleOrderSetMessage = new StringJoiner(", ", "Created sample orders. ", ".");
        for (SharedOrderStatus orderStatus : SharedOrderStatus.values()) {
            String orderReference = adminTemporaryCreateSampleOrder(
                    orderStatus, null, null, null, adminTemporaryCreateSampleOrderSetAdminUserID);
            String adminTemporaryCreateSampleOrderSetStatusWord = orderStatus.name().replace('_', ' ')
                    .toLowerCase(Locale.ROOT);
            adminTemporaryCreateSampleOrderSetMessage.add(orderReference + " ("
                    + Character.toUpperCase(adminTemporaryCreateSampleOrderSetStatusWord.charAt(0))
                    + adminTemporaryCreateSampleOrderSetStatusWord.substring(1) + ")");
        }
        return new SharedMessageResponse(adminTemporaryCreateSampleOrderSetMessage.toString());
    }

    // ===== SECTION 14: adminTemporaryCreateShortageReport =====

    // TEMPORARY TEST CODE - remove before integration
    /**
     * What it does: Reports a shortage of the mug on a new sample order, or the pen on a named sample order.
     * Input: The status for a new order, the missing quantity, and an optional sample order reference.
     * Output: The new case number and order reference, or a validation or shortage error.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryCreateShortageReport(
            AdminTemporaryCreateShortageReportRequest adminTemporaryCreateShortageReportRequest) {
        // With an order reference, keep the existing order and report its pen.
        String orderReference = adminTemporaryCreateShortageReportRequest.adminTemporaryCreateShortageReportOrderReference();
        boolean adminTemporaryCreateShortageReportIsNewOrder = orderReference == null || orderReference.isBlank();
        SharedOrderStatus orderStatus = adminTemporaryCreateShortageReportRequest.adminTemporaryCreateShortageReportOrderStatus();
        if (adminTemporaryCreateShortageReportIsNewOrder && orderStatus != SharedOrderStatus.AWAITING_PAYMENT
                && orderStatus != SharedOrderStatus.PICKING && orderStatus != SharedOrderStatus.READY_FOR_PICKUP) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID", "Choose a sample order status.");
        }

        // For a new order, the reporter is also the worker picked by the sample builder.
        Long reportedByWorkerID = adminTemporaryCreateTestUser(SharedUserRole.WAREHOUSE_WORKER,
                SharedPasswordRule.sharedCreateTemporaryPassword());
        orderReference = adminTemporaryCreateShortageReportIsNewOrder
                ? adminTemporaryCreateSampleOrder(orderStatus, null, reportedByWorkerID, null)
                : orderReference.strip();
        Long orderID = adminTemporaryCreateTestUserDatabase.sql("""
                SELECT order_id FROM customer_order WHERE order_reference = :orderReference AND order_reference LIKE 'TEST-%'
                """).param("orderReference", orderReference).query(Long.class).optional()
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "SHARED_NOT_FOUND",
                        "This sample order was not found."));

        // Use only the sample SKU belonging to the selected order, then call the Warehouse input function.
        Long productID = adminTemporaryCreateTestUserDatabase.sql("""
                SELECT item.product_id FROM order_item item JOIN product USING (product_id)
                WHERE item.order_id = :orderID AND product.sku LIKE :skuPattern
                """).param("orderID", orderID)
                .param("skuPattern", adminTemporaryCreateShortageReportIsNewOrder ? "TEST-MUG-%" : "TEST-PEN-%")
                .query(Long.class).optional()
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "SHARED_NOT_FOUND",
                        "This sample order does not have the item to report."));
        Integer missingQuantity = adminTemporaryCreateShortageReportRequest.adminTemporaryCreateShortageReportMissingQuantity();
        long shortageCaseID = adminTemporaryCreateShortageReportInput.adminReceiveShortageReportPlaceholder(
                orderID, productID, missingQuantity == null ? 1 : missingQuantity,
                adminTemporaryCreateShortageReportIsNewOrder ? "1 mug cracked on shelf" : null, reportedByWorkerID);
        return new SharedMessageResponse("Created shortage case " + shortageCaseID + " for order " + orderReference + ".");
    }

    // ===== SECTION 15: adminTemporaryResolveShortageCase =====

    // TEMPORARY TEST CODE - remove before integration
    /**
     * What it does: Simulates a Warehouse Worker resolving a case after restock.
     * Input: The case number.
     * Output: A shared success message, or a shortage error.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryResolveShortageCase(
            AdminTemporaryResolveShortageCaseRequest adminTemporaryResolveShortageCaseRequest) {
        Long shortageCaseID = adminTemporaryResolveShortageCaseRequest.adminTemporaryResolveShortageCaseShortageCaseID();
        if (shortageCaseID == null || shortageCaseID < 1) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID", "Enter a case number.");
        }

        // The same Warehouse input validates and closes the case.
        Long resolvedByWorkerID = adminTemporaryCreateTestUser(SharedUserRole.WAREHOUSE_WORKER,
                SharedPasswordRule.sharedCreateTemporaryPassword());
        adminTemporaryCreateShortageReportInput.adminReceiveShortageResolvedPlaceholder(shortageCaseID, resolvedByWorkerID);
        return new SharedMessageResponse("Shortage case " + shortageCaseID + " was resolved.");
    }

    // ===== SECTION 16: adminTemporaryCreatePickingOrder =====

    // TEMPORARY TEST CODE - remove before integration
    /**
     * What it does: Creates a sample order with its first accepted mug scan and assigned worker.
     * Input: An optional active Warehouse Worker username.
     * Output: The new order reference and the username, or a worker error.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryCreatePickingOrder(
            AdminTemporaryCreatePickingOrderRequest adminTemporaryCreatePickingOrderRequest) {
        String username = adminTemporaryCreatePickingOrderRequest.adminTemporaryCreatePickingOrderWorkerUsername();
        Long warehouseWorkerUserID;
        if (username == null || username.isBlank()) {
            warehouseWorkerUserID = adminTemporaryCreateTestUser(SharedUserRole.WAREHOUSE_WORKER,
                    SharedPasswordRule.sharedCreateTemporaryPassword());
            username = adminTemporaryCreateTestUserDatabase.sql("SELECT username FROM app_user WHERE user_id = :userID")
                    .param("userID", warehouseWorkerUserID).query(String.class).single();
        } else {
            AppUser adminTemporaryCreatePickingOrderWorker = adminTemporaryFindTestUser(username);
            if (adminTemporaryCreatePickingOrderWorker.role != SharedUserRole.WAREHOUSE_WORKER
                    || adminTemporaryCreatePickingOrderWorker.accountStatus != SharedAccountStatus.ACTIVE) {
                throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_WORKER_NOT_ALLOWED",
                        "Choose an active Warehouse Worker.");
            }
            warehouseWorkerUserID = adminTemporaryCreatePickingOrderWorker.userID;
            username = adminTemporaryCreatePickingOrderWorker.username;
        }

        String orderReference = adminTemporaryCreateSampleOrder(SharedOrderStatus.PICKING, null,
                warehouseWorkerUserID, null);
        return new SharedMessageResponse("Created picking order " + orderReference + " for " + username + ".");
    }

    // ===== SECTION 17: adminTemporaryCreateSampleOrder =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 2 builds matching order, stock, picking and pickup fixtures in one transaction.
    /**
     * What it does: Builds an order fixture for the Stage 2 and Stage 3 tools.
     * Input: Status plus optional Client, Warehouse Worker and Delivery Worker account IDs.
     * Output: The order reference. The caller starts the transaction before creating the fixture.
     */
    private String adminTemporaryCreateSampleOrder(SharedOrderStatus orderStatus, Long clientUserID,
            Long warehouseWorkerUserID, Long deliveryWorkerUserID) {
        return adminTemporaryCreateSampleOrder(orderStatus, clientUserID, warehouseWorkerUserID, deliveryWorkerUserID,
                null);
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 extends the shared builder with delivery attempts, returns, proofs and refunds.
    /**
     * What it does: Builds the shared order fixture with the rows needed by the requested status.
     * Input: Order status, optional client and worker IDs, and the Admin ID for an approved redelivery.
     * Output: The order reference. The caller starts the transaction before creating the fixture.
     * Placeholder: PI-05, the fixture uses the current shared status and type words.
     */
    private String adminTemporaryCreateSampleOrder(SharedOrderStatus orderStatus, Long clientUserID,
            Long warehouseWorkerUserID, Long deliveryWorkerUserID, Long adminTemporaryCreateSampleOrderAdminUserID) {
        // Work out which steps this status has reached before inserting any rows.
        // PLACEHOLDER INPUT PI-05
        // From: The other roles.
        // What: Status words written to shared order-related rows.
        // How to use: Take shared words from SharedPlaceholderWord until the team fixes their values.
        String taskStatus = switch (orderStatus) {
            case AWAITING_PAYMENT, CANCELLED -> null;
            case READY_FOR_PICKING -> SHARED_PICKING_TASK_STATUS_PENDING;
            case PICKING -> SHARED_PICKING_TASK_STATUS_IN_PROGRESS;
            case READY_FOR_PICKUP, SHIPPED, OUT_FOR_DELIVERY, DELIVERY_FAILED, READY_FOR_REDELIVERY, DELIVERED ->
                SHARED_PICKING_TASK_STATUS_PACKED;
        };
        boolean adminTemporaryCreateSampleOrderIsPicking = orderStatus == SharedOrderStatus.PICKING;
        boolean adminTemporaryCreateSampleOrderIsShipped = switch (orderStatus) {
            case SHIPPED, OUT_FOR_DELIVERY, DELIVERY_FAILED, READY_FOR_REDELIVERY, DELIVERED -> true;
            default -> false;
        };
        boolean adminTemporaryCreateSampleOrderIsDelivered = orderStatus == SharedOrderStatus.DELIVERED;
        boolean adminTemporaryCreateSampleOrderIsPacked = orderStatus == SharedOrderStatus.READY_FOR_PICKUP
                || adminTemporaryCreateSampleOrderIsShipped;
        boolean adminTemporaryCreateSampleOrderHasAssignment = adminTemporaryCreateSampleOrderIsPicking
                || adminTemporaryCreateSampleOrderIsPacked;
        if (clientUserID == null) {
            clientUserID = adminTemporaryCreateTestUser(SharedUserRole.CLIENT,
                    SharedPasswordRule.sharedCreateTemporaryPassword());
        }
        if (adminTemporaryCreateSampleOrderHasAssignment && warehouseWorkerUserID == null) {
            warehouseWorkerUserID = adminTemporaryCreateTestUser(SharedUserRole.WAREHOUSE_WORKER,
                    SharedPasswordRule.sharedCreateTemporaryPassword());
        }
        if (adminTemporaryCreateSampleOrderIsShipped && deliveryWorkerUserID == null) {
            deliveryWorkerUserID = adminTemporaryCreateTestUser(SharedUserRole.DELIVERY_WORKER,
                    SharedPasswordRule.sharedCreateTemporaryPassword());
        }

        // Keep fixture tools from choosing the same four-digit reference in simultaneous requests.
        adminTemporaryCreateTestUserDatabase.sql("""
                SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(
                    'com.lms.backend.admin.temporarytest.adminTemporaryCreateSampleOrder', 0))
                """).query(Integer.class).single();
        int adminTemporaryCreateSampleOrderReferenceNumber = adminTemporaryCreateTestUserDatabase.sql("""
                SELECT number FROM generate_series(1, 9999) AS number
                WHERE NOT EXISTS (
                    SELECT 1 FROM customer_order WHERE order_reference = 'TEST-' || lpad(number::text, 4, '0')
                )
                ORDER BY number LIMIT 1
                """).query(Integer.class).optional()
                .orElseThrow(() -> new IllegalStateException("No test order reference is available."));
        String orderReference = String.format(Locale.ROOT, "TEST-%04d", adminTemporaryCreateSampleOrderReferenceNumber);
        String adminTemporaryCreateSampleOrderMugSKU = "TEST-MUG-" + adminTemporaryCreateSampleOrderReferenceNumber;
        String adminTemporaryCreateSampleOrderPenSKU = "TEST-PEN-" + adminTemporaryCreateSampleOrderReferenceNumber;

        // Give the delivered example a story over two days, with no events in the future.
        OffsetDateTime adminTemporaryCreateSampleOrderCurrentTime = adminTemporaryCreateTestUserDatabase
                .sql("SELECT NOW()").query(OffsetDateTime.class).single();
        OffsetDateTime createdTime = adminTemporaryCreateSampleOrderIsDelivered
                ? adminTemporaryCreateSampleOrderCurrentTime.minusHours(25)
                : adminTemporaryCreateSampleOrderCurrentTime;
        OffsetDateTime adminTemporaryCreateSampleOrderPaymentTime = adminTemporaryCreateSampleOrderIsDelivered
                ? createdTime.plusMinutes(15) : createdTime;
        OffsetDateTime adminTemporaryCreateSampleOrderPickingTime = adminTemporaryCreateSampleOrderIsDelivered
                ? createdTime.plusHours(1) : createdTime;
        OffsetDateTime adminTemporaryCreateSampleOrderPackingTime = adminTemporaryCreateSampleOrderIsDelivered
                ? createdTime.plusHours(2) : createdTime;
        OffsetDateTime adminTemporaryCreateSampleOrderPickupTime = adminTemporaryCreateSampleOrderIsDelivered
                ? adminTemporaryCreateSampleOrderCurrentTime.minusMinutes(45)
                : adminTemporaryCreateSampleOrderCurrentTime;

        // Add the catalogue and balances first so the order uses the products' saved unit prices.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO product (sku, name, current_price)
                VALUES (:mugSKU, 'Test mug', 20.00), (:penSKU, 'Test pen', 5.00)
                ON CONFLICT (sku) DO NOTHING
                """).param("mugSKU", adminTemporaryCreateSampleOrderMugSKU)
                .param("penSKU", adminTemporaryCreateSampleOrderPenSKU).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO inventory (product_id, physical_stock)
                SELECT product_id, 100 FROM product WHERE sku IN (:mugSKU, :penSKU)
                ON CONFLICT (product_id) DO NOTHING
                """).param("mugSKU", adminTemporaryCreateSampleOrderMugSKU)
                .param("penSKU", adminTemporaryCreateSampleOrderPenSKU).update();
        Long orderID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO customer_order
                    (order_reference, client_user_id, order_status, created_at, payment_deadline, total_amount)
                SELECT :orderReference, :clientUserID, :orderStatus, :createdTime, :createdTime + INTERVAL '24 hours',
                       SUM(current_price * CASE WHEN sku = :mugSKU THEN 3 ELSE 1 END)
                FROM product WHERE sku IN (:mugSKU, :penSKU)
                RETURNING order_id
                """)
                .param("orderReference", orderReference)
                .param("clientUserID", clientUserID)
                .param("orderStatus", orderStatus.name())
                .param("createdTime", createdTime)
                .param("mugSKU", adminTemporaryCreateSampleOrderMugSKU)
                .param("penSKU", adminTemporaryCreateSampleOrderPenSKU)
                .query(Long.class).single();

        // Insert two items and private marker values used by later privacy checks.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO order_item (order_id, product_id, quantity, unit_price, picked_quantity)
                SELECT :orderID, product_id, CASE WHEN sku = :mugSKU THEN 3 ELSE 1 END, current_price,
                       CASE WHEN sku = :mugSKU THEN :pickedMugCount ELSE :pickedPenCount END
                FROM product WHERE sku IN (:mugSKU, :penSKU)
                """)
                .param("orderID", orderID)
                .param("mugSKU", adminTemporaryCreateSampleOrderMugSKU)
                .param("penSKU", adminTemporaryCreateSampleOrderPenSKU)
                .param("pickedMugCount", adminTemporaryCreateSampleOrderIsPacked ? 3
                        : adminTemporaryCreateSampleOrderIsPicking ? 1 : 0)
                .param("pickedPenCount", adminTemporaryCreateSampleOrderIsPacked ? 1 : 0).update();
        Long deliveryDetailID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO order_delivery_detail
                    (order_id, version_no, recipient_name, phone, address_text, created_at)
                VALUES (:orderID, 1, 'PRIVATE-RECIPIENT-MARKER', 'PRIVATE-PHONE-MARKER', 'PRIVATE-ADDRESS-MARKER',
                        :createdTime)
                RETURNING delivery_detail_id
                """).param("orderID", orderID).param("createdTime", createdTime).query(Long.class).single();
        if (orderStatus == SharedOrderStatus.AWAITING_PAYMENT) {
            return orderReference;
        }

        // An accepted payment records the checkout. Cancellation releases both reservations and requests a refund.
        Long paymentID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO payment (order_id, amount, payment_status, created_at, accepted_at)
                SELECT order_id, total_amount, :paymentStatus, :paymentTime, :paymentTime
                FROM customer_order WHERE order_id = :orderID
                RETURNING payment_id
                """).param("orderID", orderID).param("paymentStatus", SHARED_PAYMENT_STATUS_ACCEPTED)
                .param("paymentTime", adminTemporaryCreateSampleOrderPaymentTime)
                .query(Long.class).single();
        if (orderStatus == SharedOrderStatus.CANCELLED) {
            adminTemporaryCreateSampleOrderRefund(orderID, paymentID);
            return orderReference;
        }

        // Non-cancelled paid orders hold both item quantities and have a picking task.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO stock_reservation (order_item_id, payment_id, quantity, status, reserved_at)
                SELECT order_item_id, :paymentID, quantity, :reservationStatus, :paymentTime
                FROM order_item WHERE order_id = :orderID
                """).param("paymentID", paymentID).param("reservationStatus", SHARED_RESERVATION_STATUS_HELD)
                .param("paymentTime", adminTemporaryCreateSampleOrderPaymentTime)
                .param("orderID", orderID).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE inventory
                SET reserved_stock = reserved_stock + item.quantity, updated_at = NOW()
                FROM order_item item
                WHERE item.order_id = :orderID AND item.product_id = inventory.product_id
                """).param("orderID", orderID).update();
        Long pickingTaskID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO picking_task (order_id, task_status, start_time, confirm_packing_time)
                VALUES (:orderID, :taskStatus,
                        CASE WHEN :hasAssignment THEN CAST(:pickingTime AS timestamptz) END,
                        CASE WHEN :isPacked THEN CAST(:packingTime AS timestamptz) END)
                RETURNING picking_task_id
                """).param("orderID", orderID).param("taskStatus", taskStatus)
                .param("hasAssignment", adminTemporaryCreateSampleOrderHasAssignment)
                .param("pickingTime", adminTemporaryCreateSampleOrderPickingTime)
                .param("packingTime", adminTemporaryCreateSampleOrderPackingTime)
                .param("isPacked", adminTemporaryCreateSampleOrderIsPacked).query(Long.class).single();
        if (!adminTemporaryCreateSampleOrderHasAssignment) {
            return orderReference;
        }

        // The first mug scan is accepted, and packing ends the assignment.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO picking_assignment_history (picking_task_id, worker_user_id, assigned_at, ended_at)
                VALUES (:pickingTaskID, :warehouseWorkerUserID, :pickingTime,
                        CASE WHEN :isPacked THEN CAST(:packingTime AS timestamptz) END)
                """)
                .param("pickingTaskID", pickingTaskID)
                .param("warehouseWorkerUserID", warehouseWorkerUserID)
                .param("pickingTime", adminTemporaryCreateSampleOrderPickingTime)
                .param("packingTime", adminTemporaryCreateSampleOrderPackingTime)
                .param("isPacked", adminTemporaryCreateSampleOrderIsPacked).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO picking_scan_history
                    (picking_task_id, order_item_id, worker_user_id, scanned_identifier, result, scan_time)
                SELECT :pickingTaskID, item.order_item_id, :warehouseWorkerUserID, product.sku,
                       :scanResult, :pickingTime
                FROM order_item item JOIN product ON product.product_id = item.product_id
                WHERE item.order_id = :orderID AND product.sku = :mugSKU
                """)
                .param("pickingTaskID", pickingTaskID)
                .param("warehouseWorkerUserID", warehouseWorkerUserID)
                .param("scanResult", SharedScanResult.ACCEPTED.name())
                .param("pickingTime", adminTemporaryCreateSampleOrderPickingTime)
                .param("orderID", orderID)
                .param("mugSKU", adminTemporaryCreateSampleOrderMugSKU).update();
        if (!adminTemporaryCreateSampleOrderIsPacked) {
            return orderReference;
        }

        // Packing produces a parcel. Its custody changes only on collection.
        Long packageID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO delivery_package
                    (order_id, package_qr_code, custody_status, current_handler_user_id, created_at)
                VALUES (:orderID, :packageQRCode, :custodyStatus, :handlerUserID, :packingTime)
                RETURNING package_id
                """)
                .param("orderID", orderID)
                .param("packageQRCode", orderReference + "-PACKAGE")
                .param("custodyStatus", adminTemporaryCreateSampleOrderIsShipped
                        ? SHARED_CUSTODY_STATUS_DELIVERY : SHARED_CUSTODY_STATUS_WAREHOUSE)
                .param("handlerUserID", adminTemporaryCreateSampleOrderIsShipped ? deliveryWorkerUserID : null)
                .param("packingTime", adminTemporaryCreateSampleOrderPackingTime)
                .query(Long.class).single();
        if (!adminTemporaryCreateSampleOrderIsShipped) {
            return orderReference;
        }

        // Record the collected parcel and the custody event on a closed list.
        Long pickupListID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO pickup_list
                    (list_qr_code, created_by_worker_id, status, claimed_by_delivery_user_id, created_at, claimed_at)
                VALUES (:listQRCode, :warehouseWorkerUserID, :status, :deliveryWorkerUserID, :pickupTime, :pickupTime)
                RETURNING pickup_list_id
                """)
                .param("listQRCode", orderReference + "-LIST")
                .param("warehouseWorkerUserID", warehouseWorkerUserID)
                .param("status", SHARED_PICKUP_LIST_STATUS_CLOSED)
                .param("deliveryWorkerUserID", deliveryWorkerUserID)
                .param("pickupTime", adminTemporaryCreateSampleOrderPickupTime)
                .query(Long.class).single();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO pickup_list_item (pickup_list_id, package_id, collection_outcome, collected_at)
                VALUES (:pickupListID, :packageID, :collectionOutcome, :pickupTime)
                """)
                .param("pickupListID", pickupListID)
                .param("packageID", packageID)
                .param("pickupTime", adminTemporaryCreateSampleOrderPickupTime)
                .param("collectionOutcome", SHARED_COLLECTION_OUTCOME_COLLECTED).update();
        Long pickupCustodyEventID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO package_custody_event (package_id, event_type, actor_user_id, pickup_list_id, event_time)
                VALUES (:packageID, :eventType, :deliveryWorkerUserID, :pickupListID, :pickupTime)
                RETURNING custody_event_id
                """)
                .param("packageID", packageID)
                .param("eventType", SHARED_CUSTODY_EVENT_PICKED_UP)
                .param("deliveryWorkerUserID", deliveryWorkerUserID)
                .param("pickupTime", adminTemporaryCreateSampleOrderPickupTime)
                .param("pickupListID", pickupListID).query(Long.class).single();

        // The collected quantities leave the warehouse and no longer count as reserved.
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE stock_reservation SET status = :reservationStatus, consumed_at = :pickupTime
                WHERE payment_id = :paymentID
                """).param("reservationStatus", SHARED_RESERVATION_STATUS_CONSUMED)
                .param("paymentID", paymentID)
                .param("pickupTime", adminTemporaryCreateSampleOrderPickupTime).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE inventory
                SET physical_stock = physical_stock - item.quantity,
                    reserved_stock = reserved_stock - item.quantity,
                    updated_at = NOW()
                FROM order_item item
                WHERE item.order_id = :orderID AND item.product_id = inventory.product_id
                """).param("orderID", orderID).update();

        // Orders beyond collection have a delivery attempt, and a redelivery example also has a recorded return.
        if (orderStatus != SharedOrderStatus.SHIPPED) {
            adminTemporaryCreateSampleDeliveryAttempt(orderStatus, packageID, deliveryDetailID, deliveryWorkerUserID,
                    pickupCustodyEventID, adminTemporaryCreateSampleOrderCurrentTime);
        }
        if (orderStatus == SharedOrderStatus.READY_FOR_REDELIVERY) {
            adminTemporaryCreateSampleRedelivery(orderID, paymentID, packageID, deliveryDetailID, clientUserID,
                    warehouseWorkerUserID, deliveryWorkerUserID, adminTemporaryCreateSampleOrderAdminUserID);
        }
        return orderReference;
    }

    // ===== SECTION 18: HELPER FUNCTIONS =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 shows a cancelled order with a requested refund and released stock.
    /**
     * What it does: Records both released reservations and a full refund request for a cancelled sample order.
     * Input: The order and its accepted payment.
     * Output: None. Stock totals stay the same because the reservations are created directly in their released state.
     * Placeholder: PI-05, the fixture uses the shared words for released reservations and refund requests.
     */
    private void adminTemporaryCreateSampleOrderRefund(Long orderID, Long paymentID) {
        // PLACEHOLDER INPUT PI-05
        // From: Warehouse and Payment.
        // What: Released reservation and requested refund states.
        // How to use: Take the current stored states from SharedPlaceholderWord.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO stock_reservation (order_item_id, payment_id, quantity, status, released_at)
                SELECT order_item_id, :paymentID, quantity, :reservationStatus, NOW()
                FROM order_item WHERE order_id = :orderID
                """).param("paymentID", paymentID).param("reservationStatus", SHARED_RESERVATION_STATUS_RELEASED)
                .param("orderID", orderID).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO refund (payment_id, amount, refund_status)
                SELECT payment_id, amount, :refundStatus FROM payment WHERE payment_id = :paymentID
                """).param("refundStatus", SHARED_REFUND_STATUS_REQUESTED).param("paymentID", paymentID).update();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 gives each post-pickup example the appropriate attempt and proof rows.
    /**
     * What it does: Saves an in-progress, failed or successful attempt for a collected sample package.
     * Input: Order status, package, delivery details version, assigned worker, pickup event and sample finish time.
     * Output: None. A delivered sample includes one photo and one signature record.
     * Placeholder: PI-05, the fixture uses the shared attempt outcomes and failure reason.
     */
    private void adminTemporaryCreateSampleDeliveryAttempt(SharedOrderStatus orderStatus, Long packageID,
            Long deliveryDetailID, Long deliveryWorkerID, Long pickupCustodyEventID,
            OffsetDateTime adminTemporaryCreateSampleDeliveryAttemptCurrentTime) {
        // PLACEHOLDER INPUT PI-05
        // From: Delivery.
        // What: Attempt outcomes and failure reasons.
        // How to use: Take the current stored words from SharedPlaceholderWord.
        String outcome = switch (orderStatus) {
            case OUT_FOR_DELIVERY -> SHARED_ATTEMPT_OUTCOME_IN_PROGRESS;
            case DELIVERY_FAILED, READY_FOR_REDELIVERY -> SHARED_ATTEMPT_OUTCOME_FAILED;
            case DELIVERED -> SHARED_ATTEMPT_OUTCOME_DELIVERED;
            default -> throw new IllegalArgumentException("This order status does not have a sample attempt.");
        };
        OffsetDateTime startTime = orderStatus == SharedOrderStatus.DELIVERED
                ? adminTemporaryCreateSampleDeliveryAttemptCurrentTime.minusMinutes(20)
                : adminTemporaryCreateSampleDeliveryAttemptCurrentTime;
        OffsetDateTime endTime = orderStatus == SharedOrderStatus.OUT_FOR_DELIVERY
                ? null : adminTemporaryCreateSampleDeliveryAttemptCurrentTime;
        boolean adminTemporaryCreateSampleDeliveryAttemptHasFailed =
                SHARED_ATTEMPT_OUTCOME_FAILED.equals(outcome);
        String failureReason = adminTemporaryCreateSampleDeliveryAttemptHasFailed
                ? SHARED_FAILURE_REASON_CLIENT_NOT_HOME : null;
        String internalNote = adminTemporaryCreateSampleDeliveryAttemptHasFailed
                ? "Rang the bell twice, call 5550 0000 1234" : null;

        // An attempt records the delivery-details version that the worker used and the collection behind it.
        Long deliveryAttemptID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO delivery_attempt
                    (package_id, delivery_detail_id, delivery_worker_user_id, pickup_custody_event_id,
                     start_time, end_time, outcome, failure_reason, internal_note)
                VALUES (:packageID, :deliveryDetailID, :deliveryWorkerID, :pickupCustodyEventID, :startTime, :endTime,
                        :outcome, :failureReason, :internalNote)
                RETURNING delivery_attempt_id
                """).param("packageID", packageID).param("deliveryDetailID", deliveryDetailID)
                .param("deliveryWorkerID", deliveryWorkerID).param("pickupCustodyEventID", pickupCustodyEventID)
                .param("startTime", startTime).param("endTime", endTime).param("outcome", outcome)
                .param("failureReason", failureReason).param("internalNote", internalNote).query(Long.class).single();

        // The sample proof has a marker reference and no real attachment.
        if (orderStatus == SharedOrderStatus.DELIVERED) {
            adminTemporaryCreateTestUserDatabase.sql("""
                    INSERT INTO delivery_proof (delivery_attempt_id, proof_type, proof_reference)
                    VALUES (:deliveryAttemptID, :photoType, 'TEMPORARY-NO-FILE'),
                           (:deliveryAttemptID, :signatureType, 'TEMPORARY-NO-FILE')
                    """).param("deliveryAttemptID", deliveryAttemptID)
                    .param("photoType", SharedProofType.PHOTO.name())
                    .param("signatureType", SharedProofType.SIGNATURE.name()).update();
        }
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 shows the return and approval history of a redelivery order.
    /**
     * What it does: Records the synthetic return of an intact package and the client's approved redelivery request.
     * Input: The order, payment, package, delivery-details version, Client, both workers and resolving Admin.
     * Output: None. The package is back in Warehouse and its original quantities are reserved again.
     * Placeholder: PI-05, the fixture uses the currently shared custody, reservation and instruction words.
     */
    private void adminTemporaryCreateSampleRedelivery(Long orderID, Long paymentID, Long packageID,
            Long deliveryDetailID, Long clientUserID, Long warehouseWorkerUserID, Long deliveryWorkerUserID,
            Long resolvedByAdminID) {
        // PLACEHOLDER INPUT PI-05
        // From: Delivery, Warehouse and Client.
        // What: Return event types, warehouse custody, held stock and approved redelivery states.
        // How to use: Take the current stored words from SharedPlaceholderWord.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO package_custody_event (package_id, event_type, actor_user_id)
                VALUES (:packageID, :eventType, :workerUserID)
                """).param("packageID", packageID).param("eventType", SHARED_CUSTODY_EVENT_RETURN_REQUESTED)
                .param("workerUserID", deliveryWorkerUserID).update();
        Long returnCustodyEventID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO package_custody_event (package_id, event_type, actor_user_id)
                VALUES (:packageID, :eventType, :workerUserID)
                RETURNING custody_event_id
                """).param("packageID", packageID).param("eventType", SHARED_CUSTODY_EVENT_RETURN_RECEIVED)
                .param("workerUserID", warehouseWorkerUserID).query(Long.class).single();
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE delivery_package SET custody_status = :custodyStatus, current_handler_user_id = NULL
                WHERE package_id = :packageID
                """).param("custodyStatus", SHARED_CUSTODY_STATUS_WAREHOUSE).param("packageID", packageID).update();

        // An intact return restores the original allocation and adds back both inventory quantities.
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE stock_reservation SET status = :reservationStatus, consumed_at = NULL
                WHERE payment_id = :paymentID
                """).param("reservationStatus", SHARED_RESERVATION_STATUS_HELD).param("paymentID", paymentID).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE inventory
                SET physical_stock = physical_stock + item.quantity,
                    reserved_stock = reserved_stock + item.quantity,
                    updated_at = NOW()
                FROM order_item item
                WHERE item.order_id = :orderID AND item.product_id = inventory.product_id
                """).param("orderID", orderID).update();

        // The client asked through tracking and the current Admin stands in for the approver.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO client_instruction
                    (order_id, client_user_id, delivery_detail_id, return_custody_event_id, instruction_type,
                     status, source, resolved_by_admin_id, resolved_at)
                VALUES (:orderID, :clientUserID, :deliveryDetailID, :returnCustodyEventID, :instructionType,
                        :status, :source, :resolvedByAdminID, NOW())
                """).param("orderID", orderID).param("clientUserID", clientUserID)
                .param("deliveryDetailID", deliveryDetailID).param("returnCustodyEventID", returnCustodyEventID)
                .param("instructionType", SHARED_INSTRUCTION_TYPE_REDELIVER)
                .param("status", SHARED_INSTRUCTION_STATUS_APPROVED)
                .param("source", SharedInstructionSource.TRACKING_PAGE.name())
                .param("resolvedByAdminID", resolvedByAdminID).update();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 locks the named account before applying a test change to it.
    /**
     * What it does: Finds and locks the requested account in the caller's transaction.
     * Input: The typed username.
     * Output: The matching account, or a shared error if the username is empty or missing.
     */
    private AppUser adminTemporaryFindTestUser(String adminTemporaryFindTestUserUsername) {
        if (adminTemporaryFindTestUserUsername == null || adminTemporaryFindTestUserUsername.isBlank()) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "SHARED_INPUT_INVALID", "Enter a username.");
        }
        return adminTemporaryTestUserRepository.findByUsernameForChange(adminTemporaryFindTestUserUsername.trim())
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "SHARED_NOT_FOUND",
                        "This user was not found."));
    }
}
