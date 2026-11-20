package com.lms.backend.admin.log;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminLogService adminLogService;

    /**
     * What it does: Connects the log routes to the Admin log service.
     * Input: The service that runs log requests.
     * Output: A controller for the read routes.
     */
    public AdminLogController(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    // ===== SECTION 2: GET /api/admin/logs/audit =====

    /**
     * What it does: Searches the log with the supplied filters.
     * Input: Optional filters and the requested page.
     * Output: The current page with its total counts. An unreadable request answers 400.
     */
    @GetMapping("/audit")
    public AdminSearchAuditLogResponse adminSearchAuditLog(
            @RequestParam(required = false) Instant adminSearchAuditLogFromTime,
            @RequestParam(required = false) Instant adminSearchAuditLogToTime,
            @RequestParam(required = false) String adminSearchAuditLogActorUsername,
            @RequestParam(required = false) String adminSearchAuditLogAction,
            @RequestParam(required = false) String adminSearchAuditLogTargetType,
            @RequestParam(required = false) String adminSearchAuditLogTargetID,
            @RequestParam(defaultValue = "1") int adminSearchAuditLogPageNumber) {
        return adminLogService.adminSearchAuditLog(
                adminSearchAuditLogFromTime, adminSearchAuditLogToTime, adminSearchAuditLogActorUsername,
                adminSearchAuditLogAction, adminSearchAuditLogTargetType, adminSearchAuditLogTargetID,
                adminSearchAuditLogPageNumber);
    }

    // ===== SECTION 3: GET /api/admin/logs/security =====

    /**
     * What it does: Searches the log with the supplied filters.
     * Input: Optional filters and the requested page.
     * Output: The current page with its total counts. An unreadable request answers 400.
     */
    @GetMapping("/security")
    public AdminSearchSecurityLogResponse adminSearchSecurityLog(
            @RequestParam(required = false) Instant adminSearchSecurityLogFromTime,
            @RequestParam(required = false) Instant adminSearchSecurityLogToTime,
            @RequestParam(required = false) String adminSearchSecurityLogUsername,
            @RequestParam(required = false) String adminSearchSecurityLogEventType,
            @RequestParam(required = false) String adminSearchSecurityLogResult,
            @RequestParam(defaultValue = "1") int adminSearchSecurityLogPageNumber) {
        return adminLogService.adminSearchSecurityLog(
                adminSearchSecurityLogFromTime, adminSearchSecurityLogToTime, adminSearchSecurityLogUsername,
                adminSearchSecurityLogEventType, adminSearchSecurityLogResult, adminSearchSecurityLogPageNumber);
    }

    // ===== SECTION 4: GET /api/admin/logs/picking-scan =====

    /**
     * What it does: Searches the log with the supplied filters.
     * Input: Optional filters and the requested page.
     * Output: The current page with its total counts. An unreadable request answers 400.
     */
    @GetMapping("/picking-scan")
    public AdminSearchPickingScanHistoryResponse adminSearchPickingScanHistory(
            @RequestParam(required = false) Instant adminSearchPickingScanHistoryFromTime,
            @RequestParam(required = false) Instant adminSearchPickingScanHistoryToTime,
            @RequestParam(required = false) String adminSearchPickingScanHistoryOrderReference,
            @RequestParam(required = false) String adminSearchPickingScanHistoryWorkerUsername,
            @RequestParam(required = false) String adminSearchPickingScanHistoryResult,
            @RequestParam(defaultValue = "1") int adminSearchPickingScanHistoryPageNumber) {
        return adminLogService.adminSearchPickingScanHistory(
                adminSearchPickingScanHistoryFromTime, adminSearchPickingScanHistoryToTime,
                adminSearchPickingScanHistoryOrderReference, adminSearchPickingScanHistoryWorkerUsername,
                adminSearchPickingScanHistoryResult, adminSearchPickingScanHistoryPageNumber);
    }

    // ===== SECTION 5: GET /api/admin/logs/audit/csv =====

    /**
     * What it does: Downloads matching entries as CSV.
     * Input: Optional filters.
     * Output: A filtered UTF-8 attachment. An unreadable request answers 400.
     */
    @GetMapping("/audit/csv")
    public ResponseEntity<byte[]> adminDownloadAuditLogCSV(
            @RequestParam(required = false) Instant adminSearchAuditLogFromTime,
            @RequestParam(required = false) Instant adminSearchAuditLogToTime,
            @RequestParam(required = false) String adminSearchAuditLogActorUsername,
            @RequestParam(required = false) String adminSearchAuditLogAction,
            @RequestParam(required = false) String adminSearchAuditLogTargetType,
            @RequestParam(required = false) String adminSearchAuditLogTargetID) {
        return adminLogService.adminDownloadAuditLogCSV(
                adminSearchAuditLogFromTime, adminSearchAuditLogToTime, adminSearchAuditLogActorUsername,
                adminSearchAuditLogAction, adminSearchAuditLogTargetType, adminSearchAuditLogTargetID);
    }

    // ===== SECTION 6: GET /api/admin/logs/security/csv =====

    /**
     * What it does: Downloads matching entries as CSV.
     * Input: Optional filters.
     * Output: A filtered UTF-8 attachment. An unreadable request answers 400.
     */
    @GetMapping("/security/csv")
    public ResponseEntity<byte[]> adminDownloadSecurityLogCSV(
            @RequestParam(required = false) Instant adminSearchSecurityLogFromTime,
            @RequestParam(required = false) Instant adminSearchSecurityLogToTime,
            @RequestParam(required = false) String adminSearchSecurityLogUsername,
            @RequestParam(required = false) String adminSearchSecurityLogEventType,
            @RequestParam(required = false) String adminSearchSecurityLogResult) {
        return adminLogService.adminDownloadSecurityLogCSV(
                adminSearchSecurityLogFromTime, adminSearchSecurityLogToTime, adminSearchSecurityLogUsername,
                adminSearchSecurityLogEventType, adminSearchSecurityLogResult);
    }

    // ===== SECTION 7: GET /api/admin/logs/picking-scan/csv =====

    /**
     * What it does: Downloads matching entries as CSV.
     * Input: Optional filters.
     * Output: A filtered UTF-8 attachment. An unreadable request answers 400.
     */
    @GetMapping("/picking-scan/csv")
    public ResponseEntity<byte[]> adminDownloadPickingScanHistoryCSV(
            @RequestParam(required = false) Instant adminSearchPickingScanHistoryFromTime,
            @RequestParam(required = false) Instant adminSearchPickingScanHistoryToTime,
            @RequestParam(required = false) String adminSearchPickingScanHistoryOrderReference,
            @RequestParam(required = false) String adminSearchPickingScanHistoryWorkerUsername,
            @RequestParam(required = false) String adminSearchPickingScanHistoryResult) {
        return adminLogService.adminDownloadPickingScanHistoryCSV(
                adminSearchPickingScanHistoryFromTime, adminSearchPickingScanHistoryToTime,
                adminSearchPickingScanHistoryOrderReference, adminSearchPickingScanHistoryWorkerUsername,
                adminSearchPickingScanHistoryResult);
    }

    // ===== SECTION 8: GET /api/admin/logs/cleanup-status =====

    /**
     * What it does: Reads the most recent completed retention run.
     * Input: None.
     * Output: The run time, deletion counts and retention text.
     */
    @GetMapping("/cleanup-status")
    public AdminViewLogCleanupStatusResponse adminViewLogCleanupStatus() {
        return adminLogService.adminViewLogCleanupStatus();
    }
}
