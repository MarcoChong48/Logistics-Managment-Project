package com.lms.backend.admin.shortage;

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
@RequestMapping("/api/admin/shortage-cases")
public class AdminShortageController {

    // ===== SECTION 1: SERVICE =====

    private final AdminShortageService adminShortageService;

    /**
     * What it does: Connects Admin's shortage case routes to the service.
     * Input: The shortage case service.
     * Output: The controller.
     */
    public AdminShortageController(AdminShortageService adminShortageService) {
        this.adminShortageService = adminShortageService;
    }

    // ===== SECTION 2: SEARCH AND EXPORT =====

    /**
     * What it does: Lists shortage cases in the work queue or selected state.
     * Input: The optional case status and page number.
     * Output: One matching page and its counts.
     */
    @GetMapping
    public AdminSearchShortageCaseResponse adminSearchShortageCase(
            @RequestParam(required = false) String adminSearchShortageCaseCaseStatus,
            @RequestParam(defaultValue = "1") int adminSearchShortageCasePageNumber) {
        return adminShortageService.adminSearchShortageCase(
                adminSearchShortageCaseCaseStatus, adminSearchShortageCasePageNumber);
    }

    /**
     * What it does: Downloads the same shortage case fields for the selected status.
     * Input: The optional case status.
     * Output: A UTF-8 CSV attachment.
     */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> adminDownloadShortageCaseCSV(
            @RequestParam(required = false) String adminSearchShortageCaseCaseStatus) {
        return adminShortageService.adminDownloadShortageCaseCSV(adminSearchShortageCaseCaseStatus);
    }

    // ===== SECTION 3: ADMIN DECISIONS =====

    /**
     * What it does: Saves the Admin's choice to wait for restock.
     * Input: The case ID and the reason typed in the confirmation window.
     * Output: The confirmation message and saved decision, or an error.
     */
    @PostMapping("/{shortageCaseID}/wait")
    public AdminDecidedRowResponse adminDecideShortageWait(@PathVariable long shortageCaseID,
            @RequestBody AdminDecideShortageWaitRequest adminDecideShortageWaitRequest) {
        return adminShortageService.adminDecideShortageWait(shortageCaseID, adminDecideShortageWaitRequest);
    }

    /**
     * What it does: Sends the confirmed shortage cancellation to the service.
     * Input: The case ID, the stock correction confirmation and the reason.
     * Output: The saved decision or a rule error.
     */
    @PostMapping("/{shortageCaseID}/cancel")
    public AdminDecidedRowResponse adminDecideShortageCancel(@PathVariable long shortageCaseID,
            @RequestBody AdminDecideShortageCancelRequest adminDecideShortageCancelRequest) {
        return adminShortageService.adminDecideShortageCancel(shortageCaseID, adminDecideShortageCancelRequest);
    }
}
