package com.lms.backend.admin.refund;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.admin.common.AdminDecidedRowResponse;

@RestController
@RequestMapping("/api/admin/refunds")
public class AdminRefundController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminRefundService adminRefundService;

    /**
     * What it does: Connects the refund routes to the refund service.
     * Input: The refund service.
     * Output: The controller for the live refund routes.
     */
    public AdminRefundController(AdminRefundService adminRefundService) {
        this.adminRefundService = adminRefundService;
    }

    // ===== SECTION 2: GET /api/admin/refunds =====

    /**
     * What it does: Lists refund attempts that match the requested scope.
     * Input: The unresolved-only flag and page number.
     * Output: The matching refund rows and counts.
     */
    @GetMapping
    public AdminSearchRefundResponse adminSearchRefund(
            @RequestParam(defaultValue = "true") boolean adminSearchRefundIsUnresolvedOnly,
            @RequestParam(defaultValue = "1") int adminSearchRefundPageNumber) {
        return adminRefundService.adminSearchRefund(adminSearchRefundIsUnresolvedOnly, adminSearchRefundPageNumber);
    }

    // ===== SECTION 3: POST /api/admin/refunds/{refundID}/check =====

    /**
     * What it does: Asks the service to check and record the provider result.
     * Input: The refund ID in the address.
     * Output: The saved status and a message, or the usual missing-refund error.
     */
    @PostMapping("/{refundID}/check")
    public AdminCheckRefundStatusResponse adminCheckRefundStatus(@PathVariable Long refundID) {
        return adminRefundService.adminCheckRefundStatus(refundID);
    }

    // ===== SECTION 4: POST /api/admin/refunds/{refundID}/retry =====

    /**
     * What it does: Asks the service to retry a refund after a fresh provider check.
     * Input: The refund ID and the required reason.
     * Output: The saved decision, or a shared rule error when the retry cannot proceed.
     */
    @PostMapping("/{refundID}/retry")
    public AdminDecidedRowResponse adminRetryRefund(@PathVariable Long refundID,
            @RequestBody AdminRetryRefundRequest adminRetryRefundRequest) {
        return adminRefundService.adminRetryRefund(refundID, adminRetryRefundRequest);
    }

    // ===== SECTION 5: GET /api/admin/refunds/csv =====

    /**
     * What it does: Downloads the refund rows matching the unresolved filter.
     * Input: Whether to include only unresolved refunds.
     * Output: A filtered UTF-8 CSV attachment.
     */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> adminDownloadRefundCSV(
            @RequestParam(defaultValue = "true") boolean adminSearchRefundIsUnresolvedOnly) {
        return adminRefundService.adminDownloadRefundCSV(adminSearchRefundIsUnresolvedOnly);
    }
}
