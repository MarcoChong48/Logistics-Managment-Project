package com.lms.backend.warehouse;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_WAREHOUSE;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_IN_PROGRESS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PACKED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PENDING;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.FulfillmentEligibility;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedOrderStatus;
import com.lms.backend.shared.value.SharedScanResult;

@Service
public class WarehousePickingService {

    // ===== SECTION 1: LIMITS AND WORDS =====

    // The scan column holds at most 160 characters (01_create_tables.sql, picking_scan_history).
    static final int WAREHOUSE_SCAN_IDENTIFIER_MAX_LENGTH = 160;
    // Messages quote at most this much of an unknown scan so a long scanner burst stays readable.
    private static final int WAREHOUSE_SCAN_QUOTE_MAX_LENGTH = 40;

    private final JdbcClient warehousePickingDatabase;
    private final SharedCurrentUserReader warehousePickingCurrentUserReader;
    private final SharedAuditLogWriter warehousePickingAuditLogWriter;
    private final FulfillmentEligibility eligibility;
    private final WarehousePickingCommands commands;

    /**
     * What it does: Receives the database, signed-in user reader and audit writer.
     * Input: Spring components.
     * Output: A ready picking service.
     */
    public WarehousePickingService(JdbcClient warehousePickingDatabase,
            SharedCurrentUserReader warehousePickingCurrentUserReader,
            SharedAuditLogWriter warehousePickingAuditLogWriter, FulfillmentEligibility eligibility,
            WarehousePickingCommands commands) {
        this.eligibility = eligibility;
        this.commands = commands;
        this.warehousePickingDatabase = warehousePickingDatabase;
        this.warehousePickingCurrentUserReader = warehousePickingCurrentUserReader;
        this.warehousePickingAuditLogWriter = warehousePickingAuditLogWriter;
    }

    // ===== SECTION 2: warehouseViewPickingTask =====

    /**
     * What it does: Reads the picking task of one order for the signed-in worker.
     * Input: The order reference.
     * Output: The task, its lines and the allowed actions. Another worker's active task is refused.
     */
    @Transactional(readOnly = true)
    public WarehouseViewPickingTaskResponse warehouseViewPickingTask(String orderReference) {
        Long userID = warehousePickingCurrentUserReader.sharedReadCurrentUser().userID();
        WarehouseTaskState warehouseViewPickingTaskState = warehouseReadTaskState(orderReference, false);
        warehouseCheckCanView(warehouseViewPickingTaskState, userID);
        return warehouseBuildTaskResponse(warehouseViewPickingTaskState, userID);
    }

    // ===== SECTION 3: warehouseRecordScan =====

    /**
     * What it does: Records one scan. A matching SKU adds one picked unit and starts picking on the first unit.
     * Input: The order reference and the scanned text.
     * Output: ACCEPTED or REJECTED with a page message and the updated task.
     * Placeholder: PO-09. The scanned text is compared with the exact SKU saved by Admin.
     */
    @Transactional
    public WarehouseRecordScanResponse warehouseRecordScan(String orderReference,
            WarehouseRecordScanRequest warehouseRecordScanRequest) {
        Long userID = warehousePickingCurrentUserReader.sharedReadCurrentUser().userID();
        String scannedText = warehouseRecordScanRequest == null || warehouseRecordScanRequest.scannedIdentifier() == null
                ? "" : warehouseRecordScanRequest.scannedIdentifier();
        if (scannedText.isBlank()) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "WAREHOUSE_SCAN_EMPTY", "Scan or enter a SKU.");
        }
        String storedText = scannedText.length() > WAREHOUSE_SCAN_IDENTIFIER_MAX_LENGTH
                ? scannedText.substring(0, WAREHOUSE_SCAN_IDENTIFIER_MAX_LENGTH) : scannedText;

        // Lock the order first, then its task and assignment, in the same order as the Admin reassignment.
        WarehouseTaskState state = warehouseReadTaskState(orderReference, true);
        return commands.run(state.orderID(), userID, warehouseRecordScanRequest.requestId(), List.of("scan", scannedText),
                () -> applyScan(state, userID, scannedText, storedText));
    }

    private WarehouseRecordScanResponse applyScan(WarehouseTaskState state, Long userID, String scannedText,
            String storedText) {
        boolean isAvailable = warehouseIsAvailable(state);
        if (!isAvailable && !warehouseIsMineActive(state, userID)) {
            throw warehouseRefuseChange(state, userID);
        }
        requireEligiblePayment(state.orderID());
        if (state.pickingTaskID() == null) {
            // An order can reach Ready for picking before any module created its task row.
            warehouseCreatePendingTask(state.orderID());
            state = warehouseReadTaskState(state.orderReference(), true);
        }

        // PLACEHOLDER INPUT PO-09
        // From: Admin.
        // What: The product QR code holds only the product's stored SKU.
        // How to use: Compare the scanned text with the SKU of each order item, exactly.
        WarehouseTaskLine line = warehousePickingDatabase.sql("""
                SELECT item.order_item_id, product.sku, product.name, item.quantity, item.picked_quantity
                FROM order_item item
                JOIN product ON product.product_id = item.product_id
                WHERE item.order_id = :orderID AND product.sku = :sku
                FOR UPDATE OF item
                """).param("orderID", state.orderID()).param("sku", scannedText)
                .query(WarehousePickingService::warehouseReadTaskLine).optional().orElse(null);

        // A wrong product or a full line is saved as a rejected scan and changes no quantity.
        if (line == null) {
            String quoted = warehouseQuoteScan(storedText);
            warehouseInsertScan(state.pickingTaskID(), null, userID, storedText, SharedScanResult.REJECTED,
                    "Wrong product. " + quoted + " is not on this order. No unit was added.", null);
            return new WarehouseRecordScanResponse(SharedScanResult.REJECTED.name(),
                    quoted + " is not on this order. Check the product label.",
                    warehouseBuildTaskResponse(warehouseReadTaskState(state.orderReference(), false), userID));
        }
        if (line.pickedQuantity() >= line.orderedQuantity()) {
            warehouseInsertScan(state.pickingTaskID(), line.orderItemID(), userID, line.sku(), SharedScanResult.REJECTED,
                    "Extra unit. All " + line.orderedQuantity() + " units of " + line.sku() + " " + line.productName()
                            + " were already picked. No unit was added.", null);
            return new WarehouseRecordScanResponse(SharedScanResult.REJECTED.name(),
                    line.sku() + ": all ordered units have already been picked.",
                    warehouseBuildTaskResponse(warehouseReadTaskState(state.orderReference(), false), userID));
        }

        // The first accepted unit claims an available order for this worker.
        if (isAvailable) {
            warehouseStartPicking(state, userID);
        }
        warehouseInsertScan(state.pickingTaskID(), line.orderItemID(), userID, line.sku(), SharedScanResult.ACCEPTED,
                null, null);
        int newPickedQuantity = line.pickedQuantity() + 1;
        warehousePickingDatabase.sql("UPDATE order_item SET picked_quantity = :pickedQuantity WHERE order_item_id = :id")
                .param("pickedQuantity", newPickedQuantity).param("id", line.orderItemID()).update();

        WarehouseViewPickingTaskResponse task = warehouseBuildTaskResponse(
                warehouseReadTaskState(state.orderReference(), false), userID);
        boolean isComplete = task.lineList().stream()
                .allMatch(taskLine -> taskLine.pickedQuantity() == taskLine.orderedQuantity());
        return new WarehouseRecordScanResponse(SharedScanResult.ACCEPTED.name(),
                "Picked 1 unit of " + line.sku() + ". " + newPickedQuantity + " of " + line.orderedQuantity()
                        + " picked for this item." + (isComplete ? " All units picked." : ""),
                task);
    }

    // ===== SECTION 4: warehouseLowerPickedQuantity =====

    /**
     * What it does: Corrects a line downwards by reversing its latest accepted scans.
     * Input: The order reference, the order item ID and the lower picked quantity.
     * Output: REVERSED with a page message and the updated task, or a refusal.
     */
    @Transactional
    public WarehouseRecordScanResponse warehouseLowerPickedQuantity(String orderReference, Long orderItemID,
            WarehouseLowerPickedQuantityRequest warehouseLowerPickedQuantityRequest) {
        Long userID = warehousePickingCurrentUserReader.sharedReadCurrentUser().userID();
        if (warehouseLowerPickedQuantityRequest == null || warehouseLowerPickedQuantityRequest.pickedQuantity() == null) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "WAREHOUSE_PICKED_QUANTITY_INVALID",
                    "Provide a lower picked quantity.");
        }
        Integer newPickedQuantity = warehouseLowerPickedQuantityRequest.pickedQuantity();
        String reason = warehouseLowerPickedQuantityRequest.reason() == null
                ? "" : warehouseLowerPickedQuantityRequest.reason().strip();
        if (reason.isBlank() || reason.codePointCount(0, reason.length()) > 300) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "WAREHOUSE_REVERSAL_REASON_INVALID",
                    "Give a reversal reason of up to 300 characters.");
        }
        WarehouseTaskState state = warehouseReadTaskState(orderReference, true);
        return commands.run(state.orderID(), userID, warehouseLowerPickedQuantityRequest.requestId(),
                List.of("reverse", orderItemID, newPickedQuantity, reason),
                () -> applyReversal(state, userID, orderItemID, newPickedQuantity, reason));
    }

    private WarehouseRecordScanResponse applyReversal(WarehouseTaskState state, Long userID, Long orderItemID,
            int newPickedQuantity, String reason) {
        if (!warehouseIsMineActive(state, userID)) {
            throw warehouseRefuseChange(state, userID);
        }
        WarehouseTaskLine line = warehousePickingDatabase.sql("""
                SELECT item.order_item_id, product.sku, product.name, item.quantity, item.picked_quantity
                FROM order_item item
                JOIN product ON product.product_id = item.product_id
                WHERE item.order_id = :orderID AND item.order_item_id = :orderItemID
                FOR UPDATE OF item
                """).param("orderID", state.orderID()).param("orderItemID", orderItemID)
                .query(WarehousePickingService::warehouseReadTaskLine).optional()
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "WAREHOUSE_ITEM_NOT_FOUND",
                        "This item is not on order " + state.orderReference() + "."));
        if (newPickedQuantity < 0 || newPickedQuantity >= line.pickedQuantity()) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "WAREHOUSE_PICKED_QUANTITY_INVALID",
                    "Choose a quantity lower than the " + line.pickedQuantity() + " already picked.");
        }

        // Each removed unit reverses one accepted scan, newest first, so the scan history stays complete.
        int quantityToReverse = line.pickedQuantity() - newPickedQuantity;
        List<Long> reversedScanIDList = warehousePickingDatabase.sql("""
                SELECT scan.scan_id FROM picking_scan_history scan
                WHERE scan.picking_task_id = :pickingTaskID AND scan.order_item_id = :orderItemID
                  AND scan.result = :accepted
                  AND NOT EXISTS (SELECT 1 FROM picking_scan_history reversal
                                  WHERE reversal.original_scan_id = scan.scan_id)
                ORDER BY scan.scan_time DESC, scan.scan_id DESC
                LIMIT :count
                """).param("pickingTaskID", state.pickingTaskID()).param("orderItemID", line.orderItemID())
                .param("accepted", SharedScanResult.ACCEPTED.name())
                .param("count", quantityToReverse)
                .query(Long.class).list();
        if (reversedScanIDList.size() != quantityToReverse) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "WAREHOUSE_SCAN_HISTORY_INCONSISTENT",
                    "The recorded scans do not support that quantity correction.");
        }
        for (Long originalScanID : reversedScanIDList) {
            warehouseInsertScan(state.pickingTaskID(), line.orderItemID(), userID, line.sku(),
                    SharedScanResult.REVERSED, reason, originalScanID);
        }
        warehousePickingDatabase.sql("UPDATE order_item SET picked_quantity = :pickedQuantity WHERE order_item_id = :id")
                .param("pickedQuantity", newPickedQuantity).param("id", line.orderItemID()).update();

        Map<String, Object> oldValue = new LinkedHashMap<>();
        oldValue.put("orderItemID", line.orderItemID());
        oldValue.put("pickedQuantity", line.pickedQuantity());
        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("orderItemID", line.orderItemID());
        newValue.put("pickedQuantity", newPickedQuantity);
        warehousePickingAuditLogWriter.sharedWriteAuditLog(userID,
                WarehouseAuditAction.WAREHOUSE_AUDIT_ACTION_PICKED_QUANTITY_LOWERED,
                WarehouseAuditAction.WAREHOUSE_AUDIT_TARGET_PICKING_TASK, String.valueOf(state.pickingTaskID()),
                oldValue, newValue);
        return new WarehouseRecordScanResponse(SharedScanResult.REVERSED.name(), "Picked quantity updated.",
                warehouseBuildTaskResponse(warehouseReadTaskState(state.orderReference(), false), userID));
    }

    // ===== SECTION 5: warehouseConfirmPacking =====

    /**
     * What it does: Confirms that every unit is packed and hands the order to delivery pickup.
     * Input: The order reference.
     * Output: The packed task. The order becomes Ready for pickup and one package is created in the warehouse.
     */
    @Transactional
    public WarehouseRecordScanResponse warehouseConfirmPacking(String orderReference) {
        Long userID = warehousePickingCurrentUserReader.sharedReadCurrentUser().userID();
        WarehouseTaskState state = warehouseReadTaskState(orderReference, true);
        if (warehouseIsOwnPacked(state, userID)) {
            return new WarehouseRecordScanResponse("PACKED", "Packing was already confirmed.",
                    warehouseBuildTaskResponse(state, userID));
        }
        if (!warehouseIsMineActive(state, userID)) {
            throw warehouseRefuseChange(state, userID);
        }
        requireEligiblePayment(state.orderID());
        List<WarehouseTaskLine> lineList = warehouseReadTaskLineList(state.orderID());
        if (lineList.isEmpty() || lineList.stream().anyMatch(line -> line.pickedQuantity() != line.orderedQuantity())) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "WAREHOUSE_PICKING_INCOMPLETE",
                    "Confirmation requires every ordered unit to be picked.");
        }
        if (warehouseHasOpenShortage(state.orderID())) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "WAREHOUSE_SHORTAGE_OPEN",
                    "A shortage case for this order is waiting for an Admin decision.");
        }
        if (warehousePickingDatabase.sql("SELECT COUNT(*) FROM delivery_package WHERE order_id = :orderID")
                .param("orderID", state.orderID()).query(Long.class).single() > 0) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "WAREHOUSE_PACKAGE_EXISTS",
                    "Order " + state.orderReference() + " already has a package.");
        }

        // Close the task and its assignment, then create the package Delivery collects.
        warehousePickingDatabase.sql("""
                UPDATE picking_task SET task_status = :packed, confirm_packing_time = NOW()
                WHERE picking_task_id = :pickingTaskID
                """).param("packed", SHARED_PICKING_TASK_STATUS_PACKED)
                .param("pickingTaskID", state.pickingTaskID()).update();
        warehousePickingDatabase.sql("UPDATE picking_assignment_history SET ended_at = NOW() WHERE assignment_id = :id")
                .param("id", state.openAssignmentID()).update();
        // Reservations stay HELD here. Delivery consumes them when the package is collected.
        warehousePickingDatabase.sql("""
                INSERT INTO delivery_package (order_id, package_qr_code, custody_status, current_handler_user_id)
                VALUES (:orderID, :packageQRCode, :custodyStatus, NULL)
                """).param("orderID", state.orderID()).param("packageQRCode", state.orderReference() + "-PACKAGE")
                .param("custodyStatus", SHARED_CUSTODY_STATUS_WAREHOUSE).update();
        warehouseSetOrderStatus(state.orderID(), SharedOrderStatus.READY_FOR_PICKUP);

        Map<String, Object> oldValue = new LinkedHashMap<>();
        oldValue.put("orderStatus", SharedOrderStatus.PICKING.name());
        oldValue.put("taskStatus", SHARED_PICKING_TASK_STATUS_IN_PROGRESS);
        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("orderStatus", SharedOrderStatus.READY_FOR_PICKUP.name());
        newValue.put("taskStatus", SHARED_PICKING_TASK_STATUS_PACKED);
        newValue.put("custodyStatus", SHARED_CUSTODY_STATUS_WAREHOUSE);
        warehousePickingAuditLogWriter.sharedWriteAuditLog(userID,
                WarehouseAuditAction.WAREHOUSE_AUDIT_ACTION_PACKING_CONFIRMED,
                WarehouseAuditAction.WAREHOUSE_AUDIT_TARGET_PICKING_TASK, String.valueOf(state.pickingTaskID()),
                oldValue, newValue);
        return new WarehouseRecordScanResponse("PACKED", "Packing confirmed for order " + state.orderReference() + ".",
                warehouseBuildTaskResponse(warehouseReadTaskState(orderReference, false), userID));
    }

    // ===== SECTION 6: TASK STATE =====

    /**
     * What it does: Holds the order, task and assignment facts that every picking rule reads.
     */
    record WarehouseTaskState(Long orderID, String orderReference, String orderStatus, Long pickingTaskID,
            String taskStatus, Instant startTime, Instant confirmPackingTime, Long openAssignmentID,
            Long openWorkerUserID, Long lastWorkerUserID, String shownWorkerUsername) {
    }

    /**
     * What it does: Reads the order, its task and its assignments, optionally locking them in a fixed order.
     * Input: The order reference and whether to lock the rows.
     * Output: The task state, or 404 when no order at the warehouse stage has this reference.
     */
    WarehouseTaskState warehouseReadTaskState(String orderReference, boolean lock) {
        String lockText = lock ? " FOR UPDATE" : "";
        Object[] order = warehousePickingDatabase.sql("""
                SELECT order_id, order_reference, order_status FROM customer_order
                WHERE order_reference = :orderReference AND order_status <> :awaitingPayment""" + lockText)
                .param("orderReference", orderReference == null ? "" : orderReference.strip())
                .param("awaitingPayment", SharedOrderStatus.AWAITING_PAYMENT.name())
                .query((resultSet, rowNumber) -> new Object[] {resultSet.getLong(1), resultSet.getString(2),
                        resultSet.getString(3)})
                .optional()
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "WAREHOUSE_ORDER_NOT_FOUND",
                        "No order with ID " + warehouseQuoteScan(orderReference == null ? "" : orderReference) + "."));
        Long orderID = (Long) order[0];
        Object[] task = warehousePickingDatabase.sql("""
                SELECT picking_task_id, task_status, start_time, confirm_packing_time FROM picking_task
                WHERE order_id = :orderID""" + lockText)
                .param("orderID", orderID)
                .query((resultSet, rowNumber) -> new Object[] {resultSet.getLong(1), resultSet.getString(2),
                        warehouseReadInstant(resultSet, 3), warehouseReadInstant(resultSet, 4)})
                .optional().orElse(new Object[] {null, null, null, null});
        Long pickingTaskID = (Long) task[0];
        Long openAssignmentID = null;
        Long openWorkerUserID = null;
        Long lastWorkerUserID = null;
        if (pickingTaskID != null) {
            Object[] openAssignment = warehousePickingDatabase.sql("""
                    SELECT assignment_id, worker_user_id FROM picking_assignment_history
                    WHERE picking_task_id = :pickingTaskID AND ended_at IS NULL""" + lockText)
                    .param("pickingTaskID", pickingTaskID)
                    .query((resultSet, rowNumber) -> new Object[] {resultSet.getLong(1), resultSet.getLong(2)})
                    .optional().orElse(new Object[] {null, null});
            openAssignmentID = (Long) openAssignment[0];
            openWorkerUserID = (Long) openAssignment[1];
            lastWorkerUserID = warehousePickingDatabase.sql("""
                    SELECT worker_user_id FROM picking_assignment_history WHERE picking_task_id = :pickingTaskID
                    ORDER BY assigned_at DESC, assignment_id DESC LIMIT 1
                    """).param("pickingTaskID", pickingTaskID).query(Long.class).optional().orElse(null);
        }
        Long shownWorkerUserID = openWorkerUserID != null ? openWorkerUserID : lastWorkerUserID;
        String shownWorkerUsername = shownWorkerUserID == null ? null : warehousePickingDatabase
                .sql("SELECT username FROM app_user WHERE user_id = :userID")
                .param("userID", shownWorkerUserID).query(String.class).single();
        return new WarehouseTaskState(orderID, (String) order[1], (String) order[2], pickingTaskID, (String) task[1],
                (Instant) task[2], (Instant) task[3], openAssignmentID, openWorkerUserID, lastWorkerUserID,
                shownWorkerUsername);
    }

    /**
     * What it does: Tells whether nobody has started picking an order that is ready for picking.
     * Input: The task state.
     * Output: True when any worker may start it.
     */
    static boolean warehouseIsAvailable(WarehouseTaskState state) {
        return SharedOrderStatus.READY_FOR_PICKING.name().equals(state.orderStatus())
                && (state.pickingTaskID() == null || SHARED_PICKING_TASK_STATUS_PENDING.equals(state.taskStatus()))
                && state.openAssignmentID() == null;
    }

    /**
     * What it does: Tells whether the signed-in worker holds the active assignment of an order being picked.
     * Input: The task state and the worker's user ID.
     * Output: True only for an in-progress task assigned to this worker (PO-04 ownership).
     */
    static boolean warehouseIsMineActive(WarehouseTaskState state, Long userID) {
        return SharedOrderStatus.PICKING.name().equals(state.orderStatus())
                && SHARED_PICKING_TASK_STATUS_IN_PROGRESS.equals(state.taskStatus())
                && Objects.equals(state.openWorkerUserID(), userID);
    }

    /**
     * What it does: Refuses to show a task the worker may not open.
     * Input: The task state and the worker's user ID.
     * Output: None for an available, own active or own packed task. Otherwise a 403 or 409 refusal.
     */
    private static void warehouseCheckCanView(WarehouseTaskState state, Long userID) {
        if (warehouseIsAvailable(state) || warehouseIsMineActive(state, userID) || warehouseIsOwnPacked(state, userID)) {
            return;
        }
        throw warehouseRefuseChange(state, userID);
    }

    /**
     * What it does: Chooses the refusal for an action on a task that is not available or not this worker's.
     * Input: The task state and the worker's user ID.
     * Output: The exception to throw.
     */
    private static SharedRuleException warehouseRefuseChange(WarehouseTaskState state, Long userID) {
        if (state.openWorkerUserID() != null && !Objects.equals(state.openWorkerUserID(), userID)) {
            return new SharedRuleException(HttpStatus.FORBIDDEN, "WAREHOUSE_TASK_NOT_YOURS",
                    "Order " + state.orderReference() + " is being picked by another worker.");
        }
        return new SharedRuleException(HttpStatus.CONFLICT, "WAREHOUSE_TASK_CLOSED",
                "Order " + state.orderReference() + " is not open for picking.");
    }

    /**
     * What it does: Builds the page view of a task for the signed-in worker.
     * Input: The task state and the worker's user ID.
     * Output: The task response with its lines and allowed actions.
     */
    private WarehouseViewPickingTaskResponse warehouseBuildTaskResponse(WarehouseTaskState state, Long userID) {
        List<WarehouseTaskLine> lineList = warehouseReadTaskLineList(state.orderID());
        boolean isMineActive = warehouseIsMineActive(state, userID);
        boolean hasOpenShortage = warehouseHasOpenShortage(state.orderID());
        boolean isComplete = !lineList.isEmpty()
                && lineList.stream().allMatch(line -> line.pickedQuantity() == line.orderedQuantity());
        return new WarehouseViewPickingTaskResponse(state.orderReference(), state.orderStatus(),
                state.taskStatus() == null ? SHARED_PICKING_TASK_STATUS_PENDING : state.taskStatus(),
                state.shownWorkerUsername(), Objects.equals(state.openWorkerUserID(), userID)
                        || (state.openAssignmentID() == null && Objects.equals(state.lastWorkerUserID(), userID)),
                warehouseIsAvailable(state) || isMineActive, isMineActive && isComplete && !hasOpenShortage,
                hasOpenShortage, state.startTime(), state.confirmPackingTime(), lineList);
    }

    private static boolean warehouseIsOwnPacked(WarehouseTaskState state, Long userID) {
        return state.openAssignmentID() == null && Objects.equals(state.lastWorkerUserID(), userID)
                && SHARED_PICKING_TASK_STATUS_PACKED.equals(state.taskStatus());
    }

    private void requireEligiblePayment(long orderId) {
        if (eligibility.heldPayment(orderId).isEmpty()) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "WAREHOUSE_PAYMENT_OR_HOLDS_UNRESOLVED",
                    "Picking and packing require an accepted payment and matching held reservations.");
        }
    }

    // ===== SECTION 7: ROW HELPERS =====

    /**
     * What it does: Reads every item of an order with its SKU, name and quantities.
     * Input: The order ID.
     * Output: The lines in order-item order.
     */
    List<WarehouseTaskLine> warehouseReadTaskLineList(Long orderID) {
        return warehousePickingDatabase.sql("""
                SELECT item.order_item_id, product.sku, product.name, item.quantity, item.picked_quantity
                FROM order_item item
                JOIN product ON product.product_id = item.product_id
                WHERE item.order_id = :orderID
                ORDER BY item.order_item_id
                """).param("orderID", orderID).query(WarehousePickingService::warehouseReadTaskLine).list();
    }

    /**
     * What it does: Maps one item row.
     * Input: The result set positioned on an item row.
     * Output: One task line.
     */
    static WarehouseTaskLine warehouseReadTaskLine(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WarehouseTaskLine(resultSet.getLong(1), resultSet.getString(2), resultSet.getString(3),
                resultSet.getInt(4), resultSet.getInt(5));
    }

    /**
     * What it does: Reads a timestamp column as an instant.
     * Input: The result set and the column number.
     * Output: The instant, or null when the column is empty.
     */
    static Instant warehouseReadInstant(ResultSet resultSet, int columnNumber) throws SQLException {
        OffsetDateTime value = resultSet.getObject(columnNumber, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /**
     * What it does: Tells whether an order has a shortage case waiting for an Admin decision or for stock.
     * Input: The order ID.
     * Output: True when an OPEN or WAITING_RESTOCK case exists (03_admin_changes.sql, shortage_case).
     */
    private boolean warehouseHasOpenShortage(Long orderID) {
        return warehousePickingDatabase.sql("""
                SELECT COUNT(*) FROM shortage_case
                WHERE order_id = :orderID AND case_status IN ('OPEN', 'WAITING_RESTOCK')
                """).param("orderID", orderID).query(Long.class).single() > 0;
    }

    /**
     * What it does: Starts picking for this worker.
     * Input: The locked state of an available order and the worker's user ID.
     * Output: An in-progress task, an open assignment, a Picking order and one audit row.
     */
    private void warehouseStartPicking(WarehouseTaskState state, Long userID) {
        warehousePickingDatabase.sql("""
                UPDATE picking_task SET task_status = :inProgress, start_time = NOW()
                WHERE picking_task_id = :pickingTaskID
                """).param("inProgress", SHARED_PICKING_TASK_STATUS_IN_PROGRESS)
                .param("pickingTaskID", state.pickingTaskID()).update();
        warehousePickingDatabase.sql("""
                INSERT INTO picking_assignment_history (picking_task_id, worker_user_id, assigned_at)
                VALUES (:pickingTaskID, :userID, NOW())
                """).param("pickingTaskID", state.pickingTaskID()).param("userID", userID).update();
        warehouseSetOrderStatus(state.orderID(), SharedOrderStatus.PICKING);

        Map<String, Object> oldValue = new LinkedHashMap<>();
        oldValue.put("orderStatus", SharedOrderStatus.READY_FOR_PICKING.name());
        oldValue.put("taskStatus", SHARED_PICKING_TASK_STATUS_PENDING);
        Map<String, Object> newValue = new LinkedHashMap<>();
        newValue.put("orderStatus", SharedOrderStatus.PICKING.name());
        newValue.put("taskStatus", SHARED_PICKING_TASK_STATUS_IN_PROGRESS);
        newValue.put("workerUserID", userID);
        warehousePickingAuditLogWriter.sharedWriteAuditLog(userID,
                WarehouseAuditAction.WAREHOUSE_AUDIT_ACTION_PICKING_STARTED,
                WarehouseAuditAction.WAREHOUSE_AUDIT_TARGET_PICKING_TASK, String.valueOf(state.pickingTaskID()),
                oldValue, newValue);
    }

    /**
     * What it does: Creates the pending task row of an order that has none.
     * Input: The locked order ID.
     * Output: One pending picking task.
     */
    private void warehouseCreatePendingTask(Long orderID) {
        warehousePickingDatabase.sql("""
                INSERT INTO picking_task (order_id, task_status) VALUES (:orderID, :pending)
                ON CONFLICT (order_id) DO NOTHING
                """).param("orderID", orderID).param("pending", SHARED_PICKING_TASK_STATUS_PENDING).update();
    }

    /**
     * What it does: Saves one scan row.
     * Input: Task, optional item, worker, stored text, result, optional reason and optional reversed scan.
     * Output: One picking_scan_history row.
     */
    private void warehouseInsertScan(Long pickingTaskID, Long orderItemID, Long userID, String scannedIdentifier,
            SharedScanResult result, String reason, Long originalScanID) {
        warehousePickingDatabase.sql("""
                INSERT INTO picking_scan_history
                    (picking_task_id, order_item_id, worker_user_id, scanned_identifier, scan_time, result, reason,
                     original_scan_id)
                VALUES (:pickingTaskID, :orderItemID, :userID, :scannedIdentifier, clock_timestamp(), :result,
                        :reason, :originalScanID)
                """).param("pickingTaskID", pickingTaskID).param("orderItemID", orderItemID).param("userID", userID)
                .param("scannedIdentifier", scannedIdentifier).param("result", result.name())
                .param("reason", reason).param("originalScanID", originalScanID).update();
    }

    /**
     * What it does: Changes the order status.
     * Input: The locked order ID and the new status.
     * Output: The updated order row.
     */
    private void warehouseSetOrderStatus(Long orderID, SharedOrderStatus orderStatus) {
        warehousePickingDatabase.sql("UPDATE customer_order SET order_status = :orderStatus WHERE order_id = :orderID")
                .param("orderStatus", orderStatus.name()).param("orderID", orderID).update();
    }

    /**
     * What it does: Shortens scanned text before it is quoted in a message.
     * Input: The scanned text.
     * Output: The text, cut with an ellipsis when it is long.
     */
    static String warehouseQuoteScan(String scannedText) {
        return scannedText.length() > WAREHOUSE_SCAN_QUOTE_MAX_LENGTH
                ? scannedText.substring(0, WAREHOUSE_SCAN_QUOTE_MAX_LENGTH) + "…" : scannedText;
    }
}
