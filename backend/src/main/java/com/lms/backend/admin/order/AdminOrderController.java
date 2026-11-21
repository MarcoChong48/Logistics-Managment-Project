package com.lms.backend.admin.order;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminOrderService adminOrderService;

    /**
     * What it does: Connects the order routes to the Admin order service.
     * Input: The order service.
     * Output: The controller for the read routes.
     */
    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    // ===== SECTION 2: GET /api/admin/orders =====

    /**
     * What it does: Searches orders using the supplied filters.
     * Input: Optional keyword, status, creation time bounds and page.
     * Output: The matching page and its total counts.
     */
    @GetMapping
    public AdminSearchOrderResponse adminSearchOrder(
            @RequestParam(required = false) String adminSearchOrderKeyword,
            @RequestParam(required = false) String adminSearchOrderOrderStatus,
            @RequestParam(required = false) Instant adminSearchOrderCreatedFromTime,
            @RequestParam(required = false) Instant adminSearchOrderCreatedToTime,
            @RequestParam(defaultValue = "1") int adminSearchOrderPageNumber) {
        return adminOrderService.adminSearchOrder(adminSearchOrderKeyword, adminSearchOrderOrderStatus,
                adminSearchOrderCreatedFromTime, adminSearchOrderCreatedToTime, adminSearchOrderPageNumber);
    }

    // ===== SECTION 3: GET /api/admin/orders/{orderID} =====

    /**
     * What it does: Opens one order's detail.
     * Input: The order ID.
     * Output: The detail or 404 when the order does not exist.
     */
    @GetMapping("/{orderID}")
    public AdminViewOrderResponse adminViewOrder(@PathVariable Long orderID) {
        return adminOrderService.adminViewOrder(orderID);
    }

    // ===== SECTION 4: GET /api/admin/orders/csv =====

    /**
     * What it does: Downloads the matching orders as CSV.
     * Input: Optional keyword, status and creation time bounds.
     * Output: A filtered UTF-8 attachment.
     */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> adminDownloadOrderCSV(
            @RequestParam(required = false) String adminSearchOrderKeyword,
            @RequestParam(required = false) String adminSearchOrderOrderStatus,
            @RequestParam(required = false) Instant adminSearchOrderCreatedFromTime,
            @RequestParam(required = false) Instant adminSearchOrderCreatedToTime) {
        return adminOrderService.adminDownloadOrderCSV(adminSearchOrderKeyword, adminSearchOrderOrderStatus,
                adminSearchOrderCreatedFromTime, adminSearchOrderCreatedToTime);
    }
}
