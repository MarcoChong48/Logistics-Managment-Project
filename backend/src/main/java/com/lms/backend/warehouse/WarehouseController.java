package com.lms.backend.warehouse;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warehouse")
public class WarehouseController {

    // ===== SECTION 1: SERVICES THIS CLASS CALLS =====

    private final WarehouseOrderService warehouseOrderService;
    private final WarehousePickingService warehousePickingService;

    /**
     * What it does: Connects the Warehouse Worker routes to the order and picking services.
     * Input: The two warehouse services.
     * Output: A controller for the warehouse routes.
     */
    public WarehouseController(WarehouseOrderService warehouseOrderService,
            WarehousePickingService warehousePickingService) {
        this.warehouseOrderService = warehouseOrderService;
        this.warehousePickingService = warehousePickingService;
    }

    // ===== SECTION 2: SUMMARY AND NOTIFICATIONS =====

    /**
     * What it does: Returns the counts shown in the warehouse side menu.
     * Input: None.
     * Output: The menu counts and recent new-order references.
     */
    @GetMapping("/summary")
    public WarehouseViewSummaryResponse warehouseViewSummary() {
        return warehouseOrderService.warehouseViewSummary();
    }

    /**
     * What it does: Lists the worker's saved notifications and recent new-order notices.
     * Input: None.
     * Output: Both notification lists.
     */
    @GetMapping("/notifications")
    public WarehouseSearchNotificationResponse warehouseSearchNotifications() {
        return warehouseOrderService.warehouseSearchNotifications();
    }

    /**
     * What it does: Marks one saved notification as read.
     * Input: The notification ID.
     * Output: An empty object, or 404 when it is not the worker's notification.
     */
    @PostMapping("/notifications/{notificationID}/read")
    public Map<String, Object> warehouseMarkNotificationRead(@PathVariable Long notificationID) {
        warehouseOrderService.warehouseMarkNotificationRead(notificationID);
        return Map.of();
    }

    /**
     * What it does: Marks all of the worker's saved notifications as read.
     * Input: None.
     * Output: The number of rows marked.
     */
    @PostMapping("/notifications/read-all")
    public Map<String, Object> warehouseMarkAllNotificationsRead() {
        return Map.of("markedCount", warehouseOrderService.warehouseMarkAllNotificationsRead());
    }

    // ===== SECTION 3: ORDER LISTS AND DETAILS =====

    /**
     * What it does: Lists orders ready for picking that nobody has started.
     * Input: None.
     * Output: The available orders.
     */
    @GetMapping("/orders/available")
    public WarehouseSearchOrderResponse warehouseSearchAvailableOrders() {
        return warehouseOrderService.warehouseSearchAvailableOrders();
    }

    /**
     * What it does: Lists the worker's in-progress picking tasks.
     * Input: None.
     * Output: The worker's active orders.
     */
    @GetMapping("/orders/mine")
    public WarehouseSearchOrderResponse warehouseSearchMyOrders() {
        return warehouseOrderService.warehouseSearchMyOrders();
    }

    /**
     * What it does: Lists orders from the warehouse stage onward.
     * Input: None.
     * Output: The newest orders at status level.
     */
    @GetMapping("/orders")
    public WarehouseSearchOrderResponse warehouseSearchOrderHistory() {
        return warehouseOrderService.warehouseSearchOrderHistory();
    }

    /**
     * What it does: Reads one order's items and milestones.
     * Input: The order reference.
     * Output: The order details.
     */
    @GetMapping("/orders/{orderReference}")
    public WarehouseViewOrderResponse warehouseViewOrder(@PathVariable String orderReference) {
        return warehouseOrderService.warehouseViewOrder(orderReference);
    }

    /**
     * What it does: Returns the order QR code as a PNG image.
     * Input: The order reference.
     * Output: PNG bytes that are not cached.
     */
    @GetMapping("/orders/{orderReference}/qr-code")
    public ResponseEntity<byte[]> warehouseViewOrderQRCode(@PathVariable String orderReference) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(warehouseOrderService.warehouseViewOrderQRCode(orderReference));
    }

    @GetMapping("/orders/{orderReference}/package")
    public Map<String, Object> warehouseViewPackage(@PathVariable String orderReference) {
        return Map.of("packageQrCode", warehouseOrderService.warehouseReadPackedPackageQR(orderReference));
    }

    @GetMapping("/orders/{orderReference}/package/qr-code")
    public ResponseEntity<byte[]> warehouseViewPackageQR(@PathVariable String orderReference) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(WarehouseOrderService.warehouseEncodeQRCode(
                        warehouseOrderService.warehouseReadPackedPackageQR(orderReference)));
    }

    // ===== SECTION 4: PICKING TASK =====

    /**
     * What it does: Reads the picking task of one order.
     * Input: The order reference.
     * Output: The task lines, progress and allowed actions.
     */
    @GetMapping("/orders/{orderReference}/picking-task")
    public WarehouseViewPickingTaskResponse warehouseViewPickingTask(@PathVariable String orderReference) {
        return warehousePickingService.warehouseViewPickingTask(orderReference);
    }

    /**
     * What it does: Records one SKU scan. The first accepted scan starts picking.
     * Input: The order reference and the scanned SKU.
     * Output: ACCEPTED or REJECTED with a message and the updated task.
     */
    @PostMapping("/orders/{orderReference}/picking-task/scans")
    public WarehouseRecordScanResponse warehouseRecordScan(@PathVariable String orderReference,
            @RequestBody(required = false) WarehouseRecordScanRequest warehouseRecordScanRequest) {
        return warehousePickingService.warehouseRecordScan(orderReference, warehouseRecordScanRequest);
    }

    /**
     * What it does: Lowers the picked quantity of one item after a mis-pick.
     * Input: The order reference, order item ID and the new lower quantity.
     * Output: REVERSED with a message and the updated task.
     */
    @PostMapping("/orders/{orderReference}/picking-task/items/{orderItemID}/picked-quantity")
    public WarehouseRecordScanResponse warehouseLowerPickedQuantity(@PathVariable String orderReference,
            @PathVariable Long orderItemID,
            @RequestBody(required = false) WarehouseLowerPickedQuantityRequest warehouseLowerPickedQuantityRequest) {
        return warehousePickingService.warehouseLowerPickedQuantity(orderReference, orderItemID,
                warehouseLowerPickedQuantityRequest);
    }

    /**
     * What it does: Confirms packing of a fully picked order.
     * Input: The order reference.
     * Output: PACKED with a message and the closed task.
     */
    @PostMapping("/orders/{orderReference}/picking-task/confirm-packing")
    public WarehouseRecordScanResponse warehouseConfirmPacking(@PathVariable String orderReference) {
        return warehousePickingService.warehouseConfirmPacking(orderReference);
    }

    // ===== SECTION 5: SCAN LOG =====

    /**
     * What it does: Lists the worker's own picking activity and scan errors.
     * Input: None.
     * Output: The activity and error rows.
     */
    @GetMapping("/activity")
    public WarehouseSearchActivityResponse warehouseSearchMyActivity() {
        return warehouseOrderService.warehouseSearchMyActivity();
    }
}
