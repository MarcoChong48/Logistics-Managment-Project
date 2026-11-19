// TEMPORARY TEST CODE - remove before integration
// Local fixtures exercise account, session, pagination and active-work rules.
package com.lms.backend.admin.temporarytest;

import static com.lms.backend.shared.login.SharedLoginRule.SHARED_TEMPORARY_PASSWORD_VALID_HOURS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_COLLECTION_OUTCOME_COLLECTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_COLLECTION_OUTCOME_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_EVENT_PICKED_UP;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_DELIVERY;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_WAREHOUSE;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_ACCEPTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_IN_PROGRESS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PACKED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKUP_LIST_STATUS_CLOSED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKUP_LIST_STATUS_OPEN;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_CONSUMED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_HELD;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.error.SharedMessageResponse;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedSecurityEvent;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.login.SharedPasswordRule;
import com.lms.backend.shared.value.SharedOrderStatus;
import com.lms.backend.shared.value.SharedScanResult;
import com.lms.backend.shared.value.SharedUserRole;

@Service
@Profile("admintest")
public class AdminTemporaryTestToolService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final AppUserRepository adminTemporaryTestUserRepository;
    private final JdbcClient adminTemporaryCreateTestUserDatabase;
    private final PasswordEncoder adminTemporaryCreateTestUserPasswordEncoder;
    private final SharedSecurityLogWriter adminTemporaryCreateTestUserLogWriter;
    private final TransactionTemplate adminTemporaryCreateTestUserTransaction;

    /**
     * What it does: Receives the database and password helpers for the local test tools.
     * Input: The repositories, encoder, transaction manager and security log writer.
     * Output: A test tools service that only loads in test mode.
     */
    public AdminTemporaryTestToolService(AppUserRepository adminTemporaryTestUserRepository,
            JdbcClient adminTemporaryCreateTestUserDatabase,
            PasswordEncoder adminTemporaryCreateTestUserPasswordEncoder,
            SharedSecurityLogWriter adminTemporaryCreateTestUserLogWriter,
            PlatformTransactionManager adminTemporaryCreateTestUserTransactionManager) {
        this.adminTemporaryTestUserRepository = adminTemporaryTestUserRepository;
        this.adminTemporaryCreateTestUserDatabase = adminTemporaryCreateTestUserDatabase;
        this.adminTemporaryCreateTestUserPasswordEncoder = adminTemporaryCreateTestUserPasswordEncoder;
        this.adminTemporaryCreateTestUserLogWriter = adminTemporaryCreateTestUserLogWriter;
        this.adminTemporaryCreateTestUserTransaction =
                new TransactionTemplate(adminTemporaryCreateTestUserTransactionManager);
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
     * Output: A message confirming the expiry time was moved, or a shared input error.
     */
    @Transactional
    public SharedMessageResponse adminTemporaryExpireTemporaryPassword(
            AdminTemporaryExpireTemporaryPasswordRequest adminTemporaryExpireTemporaryPasswordRequest) {
        AppUser adminTemporaryExpireTemporaryPasswordUser = adminTemporaryFindTestUser(
                adminTemporaryExpireTemporaryPasswordRequest.adminTemporaryExpireTemporaryPasswordUsername());
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
     * Output: A message with the saved status, or a shared input error.
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

    // ===== SECTION 9: adminTemporaryCreateSampleOrder =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 2 builds matching order, stock, picking and pickup fixtures in one transaction.
    /**
     * What it does: Builds a sample order at a status from awaiting payment through shipped.
     * Input: Status plus optional Client, Warehouse Worker and Delivery Worker account IDs.
     * Output: The order reference. The caller starts the transaction before creating the fixture.
     */
    private String adminTemporaryCreateSampleOrder(SharedOrderStatus orderStatus, Long clientUserID,
            Long warehouseWorkerUserID, Long deliveryWorkerUserID) {
        // Work out which steps this status has reached before inserting any rows.
        String taskStatus = switch (orderStatus) {
            case AWAITING_PAYMENT -> null;
            case READY_FOR_PICKING -> SHARED_PICKING_TASK_STATUS_PENDING;
            case PICKING -> SHARED_PICKING_TASK_STATUS_IN_PROGRESS;
            case READY_FOR_PICKUP, SHIPPED -> SHARED_PICKING_TASK_STATUS_PACKED;
            default -> throw new IllegalArgumentException("This order status is not supported by the test tool.");
        };
        boolean adminTemporaryCreateSampleOrderIsPicking = orderStatus == SharedOrderStatus.PICKING;
        boolean adminTemporaryCreateSampleOrderIsShipped = orderStatus == SharedOrderStatus.SHIPPED;
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
                INSERT INTO customer_order (order_reference, client_user_id, order_status, payment_deadline, total_amount)
                SELECT :orderReference, :clientUserID, :orderStatus, NOW() + INTERVAL '24 hours',
                       SUM(current_price * CASE WHEN sku = :mugSKU THEN 3 ELSE 1 END)
                FROM product WHERE sku IN (:mugSKU, :penSKU)
                RETURNING order_id
                """)
                .param("orderReference", orderReference)
                .param("clientUserID", clientUserID)
                .param("orderStatus", orderStatus.name())
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
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO order_delivery_detail (order_id, version_no, recipient_name, phone, address_text)
                VALUES (:orderID, 1, 'PRIVATE-RECIPIENT-MARKER', 'PRIVATE-PHONE-MARKER', 'PRIVATE-ADDRESS-MARKER')
                """).param("orderID", orderID).update();
        if (taskStatus == null) {
            return orderReference;
        }

        // A paid order holds both item quantities and has a picking task.
        Long paymentID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO payment (order_id, amount, payment_status, accepted_at)
                SELECT order_id, total_amount, :paymentStatus, NOW()
                FROM customer_order WHERE order_id = :orderID
                RETURNING payment_id
                """).param("orderID", orderID).param("paymentStatus", SHARED_PAYMENT_STATUS_ACCEPTED)
                .query(Long.class).single();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO stock_reservation (order_item_id, payment_id, quantity, status)
                SELECT order_item_id, :paymentID, quantity, :reservationStatus FROM order_item WHERE order_id = :orderID
                """).param("paymentID", paymentID).param("reservationStatus", SHARED_RESERVATION_STATUS_HELD)
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
                        CASE WHEN :hasAssignment THEN NOW() END, CASE WHEN :isPacked THEN NOW() END)
                RETURNING picking_task_id
                """).param("orderID", orderID).param("taskStatus", taskStatus)
                .param("hasAssignment", adminTemporaryCreateSampleOrderHasAssignment)
                .param("isPacked", adminTemporaryCreateSampleOrderIsPacked).query(Long.class).single();
        if (!adminTemporaryCreateSampleOrderHasAssignment) {
            return orderReference;
        }

        // The first mug scan is accepted, and packing ends the assignment.
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO picking_assignment_history (picking_task_id, worker_user_id, ended_at)
                VALUES (:pickingTaskID, :warehouseWorkerUserID, CASE WHEN :isPacked THEN NOW() END)
                """)
                .param("pickingTaskID", pickingTaskID)
                .param("warehouseWorkerUserID", warehouseWorkerUserID)
                .param("isPacked", adminTemporaryCreateSampleOrderIsPacked).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO picking_scan_history
                    (picking_task_id, order_item_id, worker_user_id, scanned_identifier, result)
                SELECT :pickingTaskID, item.order_item_id, :warehouseWorkerUserID, product.sku, :scanResult
                FROM order_item item JOIN product ON product.product_id = item.product_id
                WHERE item.order_id = :orderID AND product.sku = :mugSKU
                """)
                .param("pickingTaskID", pickingTaskID)
                .param("warehouseWorkerUserID", warehouseWorkerUserID)
                .param("scanResult", SharedScanResult.ACCEPTED.name())
                .param("orderID", orderID)
                .param("mugSKU", adminTemporaryCreateSampleOrderMugSKU).update();
        if (!adminTemporaryCreateSampleOrderIsPacked) {
            return orderReference;
        }

        // Packing produces a parcel. Its custody changes only on collection.
        Long packageID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO delivery_package (order_id, package_qr_code, custody_status, current_handler_user_id)
                VALUES (:orderID, :packageQRCode, :custodyStatus, :handlerUserID)
                RETURNING package_id
                """)
                .param("orderID", orderID)
                .param("packageQRCode", orderReference + "-PACKAGE")
                .param("custodyStatus", adminTemporaryCreateSampleOrderIsShipped
                        ? SHARED_CUSTODY_STATUS_DELIVERY : SHARED_CUSTODY_STATUS_WAREHOUSE)
                .param("handlerUserID", adminTemporaryCreateSampleOrderIsShipped ? deliveryWorkerUserID : null)
                .query(Long.class).single();
        if (!adminTemporaryCreateSampleOrderIsShipped) {
            return orderReference;
        }

        // Record the collected parcel and the custody event on a closed list.
        Long pickupListID = adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO pickup_list
                    (list_qr_code, created_by_worker_id, status, claimed_by_delivery_user_id, claimed_at)
                VALUES (:listQRCode, :warehouseWorkerUserID, :status, :deliveryWorkerUserID, NOW())
                RETURNING pickup_list_id
                """)
                .param("listQRCode", orderReference + "-LIST")
                .param("warehouseWorkerUserID", warehouseWorkerUserID)
                .param("status", SHARED_PICKUP_LIST_STATUS_CLOSED)
                .param("deliveryWorkerUserID", deliveryWorkerUserID)
                .query(Long.class).single();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO pickup_list_item (pickup_list_id, package_id, collection_outcome, collected_at)
                VALUES (:pickupListID, :packageID, :collectionOutcome, NOW())
                """)
                .param("pickupListID", pickupListID)
                .param("packageID", packageID)
                .param("collectionOutcome", SHARED_COLLECTION_OUTCOME_COLLECTED).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                INSERT INTO package_custody_event (package_id, event_type, actor_user_id, pickup_list_id)
                VALUES (:packageID, :eventType, :deliveryWorkerUserID, :pickupListID)
                """)
                .param("packageID", packageID)
                .param("eventType", SHARED_CUSTODY_EVENT_PICKED_UP)
                .param("deliveryWorkerUserID", deliveryWorkerUserID)
                .param("pickupListID", pickupListID).update();

        // The collected quantities leave the warehouse and no longer count as reserved.
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE stock_reservation SET status = :reservationStatus, consumed_at = NOW()
                WHERE payment_id = :paymentID
                """).param("reservationStatus", SHARED_RESERVATION_STATUS_CONSUMED)
                .param("paymentID", paymentID).update();
        adminTemporaryCreateTestUserDatabase.sql("""
                UPDATE inventory
                SET physical_stock = physical_stock - item.quantity,
                    reserved_stock = reserved_stock - item.quantity,
                    updated_at = NOW()
                FROM order_item item
                WHERE item.order_id = :orderID AND item.product_id = inventory.product_id
                """).param("orderID", orderID).update();
        return orderReference;
    }

    // ===== SECTION 10: HELPER FUNCTIONS =====

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
