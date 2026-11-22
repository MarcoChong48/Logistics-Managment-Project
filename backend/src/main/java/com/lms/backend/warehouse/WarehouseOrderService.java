package com.lms.backend.warehouse;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_ACCEPTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_IN_PROGRESS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PACKED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PENDING;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedOrderStatus;
import com.lms.backend.shared.value.SharedScanResult;

@Service
public class WarehouseOrderService {

    // ===== SECTION 1: LIMITS =====

    // All orders history is filtered and sorted in the page, so the server sends a bounded newest-first list.
    static final int WAREHOUSE_ORDER_HISTORY_ROW_LIMIT = 500;
    // A new-order notice stays listed for seven days after the payment was accepted.
    static final int WAREHOUSE_NEW_ORDER_NOTICE_DAYS = 7;
    static final int WAREHOUSE_LIST_ROW_LIMIT = 200;
    private static final int WAREHOUSE_ORDER_QR_CODE_IMAGE_SIZE = 232;

    private final JdbcClient warehouseOrderDatabase;
    private final SharedCurrentUserReader warehouseOrderCurrentUserReader;
    private final WarehousePickingService warehouseOrderPickingService;

    /**
     * What it does: Receives the database, signed-in user reader and picking rules.
     * Input: Spring components.
     * Output: A ready order service.
     */
    public WarehouseOrderService(JdbcClient warehouseOrderDatabase,
            SharedCurrentUserReader warehouseOrderCurrentUserReader,
            WarehousePickingService warehouseOrderPickingService) {
        this.warehouseOrderDatabase = warehouseOrderDatabase;
        this.warehouseOrderCurrentUserReader = warehouseOrderCurrentUserReader;
        this.warehouseOrderPickingService = warehouseOrderPickingService;
    }

    // ===== SECTION 2: warehouseViewSummary =====

    /**
     * What it does: Counts the work shown in the side menu.
     * Input: None. Uses the signed-in worker.
     * Output: Available orders, own active tasks, unread saved notifications and the recent new-order references.
     */
    @Transactional(readOnly = true)
    public WarehouseViewSummaryResponse warehouseViewSummary() {
        Long userID = warehouseOrderCurrentUserReader.sharedReadCurrentUser().userID();
        int availableOrderCount = warehouseSearchAvailableOrders().orderRowList().size();
        int myTaskCount = warehouseSearchMyOrders().orderRowList().size();
        int unreadCount = warehouseOrderDatabase.sql("""
                SELECT COUNT(*) FROM notification WHERE recipient_user_id = :userID AND is_read = FALSE
                """).param("userID", userID).query(Integer.class).single();
        List<String> newOrderReferenceList = warehouseReadNewOrderRows(userID).stream()
                .map(WarehouseNewOrderRow::orderReference).toList();
        return new WarehouseViewSummaryResponse(availableOrderCount, myTaskCount, unreadCount, newOrderReferenceList);
    }

    // ===== SECTION 3: warehouseSearchAvailableOrders =====

    /**
     * What it does: Lists orders that are ready for picking and that nobody has started.
     * Input: None.
     * Output: Up to 200 orders, oldest first so the longest-waiting order is picked first.
     */
    @Transactional(readOnly = true)
    public WarehouseSearchOrderResponse warehouseSearchAvailableOrders() {
        List<WarehouseOrderRow> orderRowList = warehouseOrderDatabase.sql("""
                SELECT o.order_reference, o.order_status, o.created_at,
                       (SELECT COALESCE(SUM(quantity), 0) FROM order_item WHERE order_id = o.order_id),
                       (SELECT COALESCE(SUM(picked_quantity), 0) FROM order_item WHERE order_id = o.order_id)
                FROM customer_order o
                LEFT JOIN picking_task task ON task.order_id = o.order_id
                WHERE o.order_status = :readyForPicking
                  AND (task.picking_task_id IS NULL OR task.task_status = :pending)
                  AND NOT EXISTS (SELECT 1 FROM picking_assignment_history assignment
                                  WHERE assignment.picking_task_id = task.picking_task_id
                                    AND assignment.ended_at IS NULL)
                ORDER BY o.created_at, o.order_reference
                LIMIT :limit
                """).param("readyForPicking", SharedOrderStatus.READY_FOR_PICKING.name())
                .param("pending", SHARED_PICKING_TASK_STATUS_PENDING).param("limit", WAREHOUSE_LIST_ROW_LIMIT)
                .query((resultSet, rowNumber) -> new WarehouseOrderRow(resultSet.getString(1), resultSet.getString(2),
                        WarehousePickingService.warehouseReadInstant(resultSet, 3), resultSet.getInt(4),
                        resultSet.getInt(5), false, null))
                .list();
        return new WarehouseSearchOrderResponse(orderRowList, orderRowList.size() >= WAREHOUSE_LIST_ROW_LIMIT);
    }

    // ===== SECTION 4: warehouseSearchMyOrders =====

    /**
     * What it does: Lists the in-progress picking tasks assigned to the signed-in worker.
     * Input: None.
     * Output: The worker's active orders with progress and the first item still to pick.
     */
    @Transactional(readOnly = true)
    public WarehouseSearchOrderResponse warehouseSearchMyOrders() {
        Long userID = warehouseOrderCurrentUserReader.sharedReadCurrentUser().userID();
        List<WarehouseOrderRow> orderRowList = warehouseOrderDatabase.sql("""
                SELECT o.order_reference, o.order_status, o.created_at,
                       (SELECT COALESCE(SUM(quantity), 0) FROM order_item WHERE order_id = o.order_id),
                       (SELECT COALESCE(SUM(picked_quantity), 0) FROM order_item WHERE order_id = o.order_id),
                       (SELECT product.sku || ' ' || product.name FROM order_item item
                        JOIN product ON product.product_id = item.product_id
                        WHERE item.order_id = o.order_id AND item.picked_quantity < item.quantity
                        ORDER BY item.order_item_id LIMIT 1)
                FROM picking_assignment_history assignment
                JOIN picking_task task ON task.picking_task_id = assignment.picking_task_id
                JOIN customer_order o ON o.order_id = task.order_id
                WHERE assignment.worker_user_id = :userID AND assignment.ended_at IS NULL
                  AND task.task_status = :inProgress AND o.order_status = :picking
                ORDER BY assignment.assigned_at, o.order_reference
                LIMIT :limit
                """).param("userID", userID).param("inProgress", SHARED_PICKING_TASK_STATUS_IN_PROGRESS)
                .param("picking", SharedOrderStatus.PICKING.name()).param("limit", WAREHOUSE_LIST_ROW_LIMIT)
                .query((resultSet, rowNumber) -> new WarehouseOrderRow(resultSet.getString(1), resultSet.getString(2),
                        WarehousePickingService.warehouseReadInstant(resultSet, 3), resultSet.getInt(4),
                        resultSet.getInt(5), true, resultSet.getString(6)))
                .list();
        return new WarehouseSearchOrderResponse(orderRowList, orderRowList.size() >= WAREHOUSE_LIST_ROW_LIMIT);
    }

    // ===== SECTION 5: warehouseSearchOrderHistory =====

    /**
     * What it does: Lists every order from the warehouse stage onward for All orders history.
     * Input: None.
     * Output: Up to 500 newest orders with status-level facts only. No client, delivery or worker details.
     */
    @Transactional(readOnly = true)
    public WarehouseSearchOrderResponse warehouseSearchOrderHistory() {
        Long userID = warehouseOrderCurrentUserReader.sharedReadCurrentUser().userID();
        List<WarehouseOrderRow> orderRowList = warehouseOrderDatabase.sql("""
                SELECT o.order_reference, o.order_status, o.created_at,
                       (SELECT COALESCE(SUM(quantity), 0) FROM order_item WHERE order_id = o.order_id),
                       (SELECT COALESCE(SUM(picked_quantity), 0) FROM order_item WHERE order_id = o.order_id),
                       EXISTS (SELECT 1 FROM picking_task task
                               JOIN picking_assignment_history assignment
                                 ON assignment.picking_task_id = task.picking_task_id
                               WHERE task.order_id = o.order_id AND assignment.ended_at IS NULL
                                 AND assignment.worker_user_id = :userID)
                FROM customer_order o
                WHERE o.order_status <> :awaitingPayment
                ORDER BY o.created_at DESC, o.order_reference DESC
                LIMIT :limit
                """).param("userID", userID).param("awaitingPayment", SharedOrderStatus.AWAITING_PAYMENT.name())
                .param("limit", WAREHOUSE_ORDER_HISTORY_ROW_LIMIT)
                .query((resultSet, rowNumber) -> new WarehouseOrderRow(resultSet.getString(1), resultSet.getString(2),
                        WarehousePickingService.warehouseReadInstant(resultSet, 3), resultSet.getInt(4),
                        resultSet.getInt(5), resultSet.getBoolean(6), null))
                .list();
        return new WarehouseSearchOrderResponse(orderRowList,
                orderRowList.size() >= WAREHOUSE_ORDER_HISTORY_ROW_LIMIT);
    }

    // ===== SECTION 6: warehouseViewOrder =====

    /**
     * What it does: Reads the status-level details of one order for its history window.
     * Input: The order reference.
     * Output: Items with SKU and quantities, and the order's warehouse milestones.
     */
    @Transactional(readOnly = true)
    public WarehouseViewOrderResponse warehouseViewOrder(String orderReference) {
        WarehousePickingService.WarehouseTaskState state =
                warehouseOrderPickingService.warehouseReadTaskState(orderReference, false);
        Object[] order = warehouseOrderDatabase.sql("""
                SELECT o.created_at,
                       (SELECT MAX(payment.accepted_at) FROM payment
                        WHERE payment.order_id = o.order_id AND payment.payment_status = :accepted)
                FROM customer_order o WHERE o.order_id = :orderID
                """).param("accepted", SHARED_PAYMENT_STATUS_ACCEPTED).param("orderID", state.orderID())
                .query((resultSet, rowNumber) -> new Object[] {WarehousePickingService.warehouseReadInstant(resultSet, 1),
                        WarehousePickingService.warehouseReadInstant(resultSet, 2)})
                .single();

        // The timeline holds only the order's own milestones, without who did them.
        List<WarehouseActivityRow> timelineRowList = new ArrayList<>();
        timelineRowList.add(new WarehouseActivityRow((java.time.Instant) order[0], state.orderReference(),
                "CREATED", "Order created."));
        if (order[1] != null) {
            timelineRowList.add(new WarehouseActivityRow((java.time.Instant) order[1], state.orderReference(),
                    "READY_FOR_PICKING", "Order ready for picking."));
        }
        if (state.startTime() != null) {
            timelineRowList.add(new WarehouseActivityRow(state.startTime(), state.orderReference(),
                    "PICKING_STARTED", "Picking started."));
        }
        if (state.confirmPackingTime() != null) {
            timelineRowList.add(new WarehouseActivityRow(state.confirmPackingTime(), state.orderReference(),
                    "PACKED", "Packing confirmed. Order ready for delivery pickup."));
        }
        timelineRowList.sort(Comparator.comparing(WarehouseActivityRow::time));
        return new WarehouseViewOrderResponse(state.orderReference(), state.orderStatus(), (java.time.Instant) order[0],
                warehouseOrderPickingService.warehouseReadTaskLineList(state.orderID()), timelineRowList);
    }

    // ===== SECTION 7: warehouseViewOrderQRCode =====

    /**
     * What it does: Draws the order QR code. A scanner that reads it types the order ID.
     * Input: The order reference.
     * Output: PNG bytes encoding the saved order reference verbatim.
     */
    @Transactional(readOnly = true)
    public byte[] warehouseViewOrderQRCode(String orderReference) {
        String savedReference = warehouseOrderPickingService.warehouseReadTaskState(orderReference, false)
                .orderReference();
        return warehouseEncodeQRCode(savedReference);
    }

    @Transactional(readOnly = true)
    public String warehouseReadPackedPackageQR(String orderReference) {
        return warehouseOrderDatabase.sql("""
                SELECT p.package_qr_code FROM customer_order o JOIN delivery_package p USING (order_id)
                JOIN picking_task t USING (order_id)
                WHERE o.order_reference = :reference AND t.task_status = 'PACKED'
                  AND t.confirm_packing_time IS NOT NULL
                """).param("reference", orderReference.strip()).query(String.class).optional()
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "WAREHOUSE_PACKAGE_NOT_FOUND",
                        "No packed package matches this order."));
    }

    static byte[] warehouseEncodeQRCode(String payload) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE,
                    WAREHOUSE_ORDER_QR_CODE_IMAGE_SIZE, WAREHOUSE_ORDER_QR_CODE_IMAGE_SIZE);
            BufferedImage image = new BufferedImage(WAREHOUSE_ORDER_QR_CODE_IMAGE_SIZE,
                    WAREHOUSE_ORDER_QR_CODE_IMAGE_SIZE, BufferedImage.TYPE_BYTE_BINARY);
            for (int rowNumber = 0; rowNumber < WAREHOUSE_ORDER_QR_CODE_IMAGE_SIZE; rowNumber++) {
                for (int columnNumber = 0; columnNumber < WAREHOUSE_ORDER_QR_CODE_IMAGE_SIZE; columnNumber++) {
                    image.setRGB(columnNumber, rowNumber,
                            matrix.get(columnNumber, rowNumber) ? 0xff000000 : 0xffffffff);
                }
            }
            ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", imageBytes)) {
                throw new IllegalStateException("The PNG writer is missing.");
            }
            return imageBytes.toByteArray();
        } catch (WriterException | IOException warehouseViewOrderQRCodeError) {
            throw new IllegalStateException("The QR code could not be generated.", warehouseViewOrderQRCodeError);
        }
    }

    // ===== SECTION 8: warehouseSearchNotifications =====

    /**
     * What it does: Lists the worker's saved notifications and the recent new-order notices.
     * Input: None.
     * Output: Saved rows newest first, and orders that became ready for picking in the last seven days.
     * Placeholder: PO-08. Admin saves assignment notices for the worker; this page shows and marks them.
     */
    @Transactional(readOnly = true)
    public WarehouseSearchNotificationResponse warehouseSearchNotifications() {
        Long userID = warehouseOrderCurrentUserReader.sharedReadCurrentUser().userID();
        // PLACEHOLDER INPUT PO-08
        // From: Admin.
        // What: Unread notification rows about an Admin decision.
        // How to use: Show rows for the signed-in recipient and allow marking them read.
        List<WarehouseNotificationRow> adminRowList = warehouseOrderDatabase.sql("""
                SELECT n.notification_id, n.type, n.message, n.created_at, n.is_read, o.order_reference, o.order_status
                FROM notification n
                LEFT JOIN customer_order o ON o.order_id = n.order_id
                WHERE n.recipient_user_id = :userID
                ORDER BY n.created_at DESC, n.notification_id DESC
                LIMIT :limit
                """).param("userID", userID).param("limit", WAREHOUSE_LIST_ROW_LIMIT)
                .query((resultSet, rowNumber) -> new WarehouseNotificationRow(resultSet.getLong(1),
                        resultSet.getString(2), resultSet.getString(3),
                        WarehousePickingService.warehouseReadInstant(resultSet, 4), resultSet.getBoolean(5),
                        resultSet.getString(6), resultSet.getString(7)))
                .list();
        return new WarehouseSearchNotificationResponse(adminRowList, warehouseReadNewOrderRows(userID));
    }

    /**
     * What it does: Finds the orders whose payment was accepted recently, which made them ready for picking.
     * Input: The worker's user ID, used to mark orders the worker is picking.
     * Output: Up to 200 notices, newest first, with each order's current status.
     */
    private List<WarehouseNewOrderRow> warehouseReadNewOrderRows(Long userID) {
        return warehouseOrderDatabase.sql("""
                SELECT o.order_reference, COALESCE(ready.accepted_at, o.created_at) AS ready_time,
                       (SELECT COALESCE(SUM(quantity), 0) FROM order_item WHERE order_id = o.order_id),
                       o.order_status,
                       EXISTS (SELECT 1 FROM picking_assignment_history assignment
                               WHERE assignment.picking_task_id = task.picking_task_id
                                 AND assignment.ended_at IS NULL AND assignment.worker_user_id = :userID)
                FROM customer_order o
                LEFT JOIN picking_task task ON task.order_id = o.order_id
                LEFT JOIN LATERAL (SELECT MAX(payment.accepted_at) AS accepted_at FROM payment
                                   WHERE payment.order_id = o.order_id AND payment.payment_status = :accepted) ready
                       ON TRUE
                WHERE o.order_status <> :awaitingPayment
                  AND COALESCE(ready.accepted_at, o.created_at) >= NOW() - make_interval(days => :days)
                ORDER BY ready_time DESC, o.order_reference DESC
                LIMIT :limit
                """).param("userID", userID).param("accepted", SHARED_PAYMENT_STATUS_ACCEPTED)
                .param("awaitingPayment", SharedOrderStatus.AWAITING_PAYMENT.name())
                .param("days", WAREHOUSE_NEW_ORDER_NOTICE_DAYS).param("limit", WAREHOUSE_LIST_ROW_LIMIT)
                .query((resultSet, rowNumber) -> new WarehouseNewOrderRow(resultSet.getString(1),
                        WarehousePickingService.warehouseReadInstant(resultSet, 2), resultSet.getInt(3),
                        resultSet.getString(4), resultSet.getBoolean(5)))
                .list();
    }

    // ===== SECTION 9: warehouseMarkNotificationRead =====

    /**
     * What it does: Marks one of the worker's own saved notifications as read.
     * Input: The notification ID.
     * Output: None, or 404 when the notification does not belong to the worker.
     */
    @Transactional
    public void warehouseMarkNotificationRead(Long notificationID) {
        Long userID = warehouseOrderCurrentUserReader.sharedReadCurrentUser().userID();
        int updatedCount = warehouseOrderDatabase.sql("""
                UPDATE notification SET is_read = TRUE
                WHERE notification_id = :notificationID AND recipient_user_id = :userID
                """).param("notificationID", notificationID).param("userID", userID).update();
        if (updatedCount == 0) {
            throw new SharedRuleException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOTIFICATION_NOT_FOUND",
                    "This notification could not be found.");
        }
    }

    /**
     * What it does: Marks all of the worker's own saved notifications as read.
     * Input: None.
     * Output: The number of rows that changed.
     */
    @Transactional
    public int warehouseMarkAllNotificationsRead() {
        Long userID = warehouseOrderCurrentUserReader.sharedReadCurrentUser().userID();
        return warehouseOrderDatabase.sql("""
                UPDATE notification SET is_read = TRUE WHERE recipient_user_id = :userID AND is_read = FALSE
                """).param("userID", userID).update();
    }

    // ===== SECTION 10: warehouseSearchMyActivity =====

    /**
     * What it does: Lists the signed-in worker's own picking activity and scan errors.
     * Input: None.
     * Output: Today's accepted and reversed scans and packing confirmations, and the latest 200 rejected scans.
     */
    @Transactional(readOnly = true)
    public WarehouseSearchActivityResponse warehouseSearchMyActivity() {
        Long userID = warehouseOrderCurrentUserReader.sharedReadCurrentUser().userID();
        List<WarehouseActivityRow> activityRowList = new ArrayList<>(warehouseOrderDatabase.sql("""
                SELECT scan.scan_time, o.order_reference, scan.result,
                       CASE WHEN scan.result = :accepted
                            THEN 'Picked 1 unit of ' || COALESCE(product.sku, scan.scanned_identifier) || '.'
                            ELSE COALESCE(scan.reason, 'Pick reversed.') END
                FROM picking_scan_history scan
                JOIN picking_task task ON task.picking_task_id = scan.picking_task_id
                JOIN customer_order o ON o.order_id = task.order_id
                LEFT JOIN order_item item ON item.order_item_id = scan.order_item_id
                LEFT JOIN product ON product.product_id = item.product_id
                WHERE scan.worker_user_id = :userID AND scan.result IN (:accepted, :reversed)
                  AND scan.scan_time >= date_trunc('day', NOW())
                ORDER BY scan.scan_time DESC, scan.scan_id DESC
                LIMIT :limit
                """).param("userID", userID).param("accepted", SharedScanResult.ACCEPTED.name())
                .param("reversed", SharedScanResult.REVERSED.name()).param("limit", WAREHOUSE_LIST_ROW_LIMIT)
                .query(WarehouseOrderService::warehouseReadActivityRow).list());
        activityRowList.addAll(warehouseOrderDatabase.sql("""
                SELECT task.confirm_packing_time, o.order_reference, 'PACKED',
                       'Packing confirmed. Order ready for delivery pickup.'
                FROM picking_task task
                JOIN customer_order o ON o.order_id = task.order_id
                WHERE task.task_status = :packed AND task.confirm_packing_time >= date_trunc('day', NOW())
                  AND (SELECT assignment.worker_user_id FROM picking_assignment_history assignment
                       WHERE assignment.picking_task_id = task.picking_task_id
                       ORDER BY assignment.assigned_at DESC, assignment.assignment_id DESC LIMIT 1) = :userID
                """).param("packed", SHARED_PICKING_TASK_STATUS_PACKED).param("userID", userID)
                .query(WarehouseOrderService::warehouseReadActivityRow).list());
        activityRowList.sort(Comparator.comparing(WarehouseActivityRow::time));
        List<WarehouseActivityRow> scanErrorRowList = new ArrayList<>(warehouseOrderDatabase.sql("""
                SELECT scan.scan_time, o.order_reference, scan.result, COALESCE(scan.reason, 'Scan rejected.')
                FROM picking_scan_history scan
                JOIN picking_task task ON task.picking_task_id = scan.picking_task_id
                JOIN customer_order o ON o.order_id = task.order_id
                WHERE scan.worker_user_id = :userID AND scan.result = :rejected
                ORDER BY scan.scan_time DESC, scan.scan_id DESC
                LIMIT :limit
                """).param("userID", userID).param("rejected", SharedScanResult.REJECTED.name())
                .param("limit", WAREHOUSE_LIST_ROW_LIMIT)
                .query(WarehouseOrderService::warehouseReadActivityRow).list());
        scanErrorRowList.sort(Comparator.comparing(WarehouseActivityRow::time));
        return new WarehouseSearchActivityResponse(activityRowList, scanErrorRowList);
    }

    /**
     * What it does: Maps one activity row.
     * Input: The result set positioned on a time, order, kind and text row.
     * Output: One activity row.
     */
    private static WarehouseActivityRow warehouseReadActivityRow(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new WarehouseActivityRow(WarehousePickingService.warehouseReadInstant(resultSet, 1),
                resultSet.getString(2), resultSet.getString(3), resultSet.getString(4));
    }
}
