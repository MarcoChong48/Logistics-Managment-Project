package com.lms.backend.admin.log;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_CSV_MAXIMUM_ROW_COUNT;
import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.admin.common.AdminPrivateValueRule.adminHidePrivateValue;
import static com.lms.backend.shared.csv.SharedCSVWriter.sharedWriteCSVDownload;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_CSV_DOWNLOADED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_LOG_CLEANUP_RUN;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.AuditLog;
import com.lms.backend.shared.database.AuditLogRepository;
import com.lms.backend.shared.database.PickingScanHistoryRepository;
import com.lms.backend.shared.database.SecurityLog;
import com.lms.backend.shared.database.SecurityLogRepository;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedScanResult;

@Service
public class AdminLogService {

    // ===== SECTION 1: CONSTANTS =====

    private static final String ADMIN_VIEW_LOG_CLEANUP_STATUS_KEEP_RULE_TEXT =
            "Security log: 90 days. Picking scan history: 90 days. Audit log: 3 years.";

    // ===== SECTION 2: OTHER CLASSES THIS CLASS USES =====

    private final AuditLogRepository adminLogAuditLogRepository;
    private final SecurityLogRepository adminLogSecurityLogRepository;
    private final PickingScanHistoryRepository adminLogPickingScanHistoryRepository;
    private final SharedAuditLogWriter adminLogAuditWriter;
    private final SharedCurrentUserReader adminLogCurrentUserReader;

    /**
     * What it does: Connects the log service to its repositories, audit writer and session reader.
     * Input: The repositories for the three logs, audit writer and current account reader.
     * Output: A service for filtered search, export and retention status.
     */
    public AdminLogService(AuditLogRepository adminLogAuditLogRepository,
            SecurityLogRepository adminLogSecurityLogRepository,
            PickingScanHistoryRepository adminLogPickingScanHistoryRepository,
            SharedAuditLogWriter adminLogAuditWriter, SharedCurrentUserReader adminLogCurrentUserReader) {
        this.adminLogAuditLogRepository = adminLogAuditLogRepository;
        this.adminLogSecurityLogRepository = adminLogSecurityLogRepository;
        this.adminLogPickingScanHistoryRepository = adminLogPickingScanHistoryRepository;
        this.adminLogAuditWriter = adminLogAuditWriter;
        this.adminLogCurrentUserReader = adminLogCurrentUserReader;
    }

    // ===== SECTION 3: adminSearchAuditLog =====

    /**
     * What it does: Reads the requested page of the log and hides private information.
     * Input: The optional filters and the requested one-based page number.
     * Output: Safe rows in newest-first order and the page totals.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchAuditLogResponse adminSearchAuditLog(
            Instant adminSearchAuditLogFromTime, Instant adminSearchAuditLogToTime,
            String adminSearchAuditLogActorUsername, String adminSearchAuditLogAction,
            String adminSearchAuditLogTargetType, String adminSearchAuditLogTargetID,
            int adminSearchAuditLogPageNumber) {
        Page<Object[]> adminSearchAuditLogPage = adminFindLogPage(adminSearchAuditLogPageNumber,
                adminSearchAuditLogPageRequest -> adminLogAuditLogRepository.adminSearchAuditLog(
                        adminSearchAuditLogFromTime, adminSearchAuditLogToTime,
                        adminReadLogFragment(adminSearchAuditLogActorUsername),
                        adminReadLogFilter(adminSearchAuditLogAction),
                        adminReadLogFilter(adminSearchAuditLogTargetType),
                        adminReadLogFilter(adminSearchAuditLogTargetID), adminSearchAuditLogPageRequest));
        List<AdminSearchAuditLogRow> adminSearchAuditLogRowList = adminSearchAuditLogPage.stream()
                .map(this::adminReadAuditLogRow).toList();
        return new AdminSearchAuditLogResponse(adminSearchAuditLogRowList, adminSearchAuditLogPage.getNumber() + 1,
                adminCountPage(adminSearchAuditLogPage.getTotalElements()), adminSearchAuditLogPage.getTotalElements());
    }

    // ===== SECTION 4: adminSearchSecurityLog =====

    /**
     * What it does: Reads the requested page of the log and hides private information.
     * Input: The optional filters and the requested one-based page number.
     * Output: Safe rows in newest-first order and the page totals.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchSecurityLogResponse adminSearchSecurityLog(
            Instant adminSearchSecurityLogFromTime, Instant adminSearchSecurityLogToTime,
            String adminSearchSecurityLogUsername, String adminSearchSecurityLogEventType,
            String adminSearchSecurityLogResult, int adminSearchSecurityLogPageNumber) {
        Page<Object[]> adminSearchSecurityLogPage = adminFindLogPage(adminSearchSecurityLogPageNumber,
                adminSearchSecurityLogPageRequest -> adminLogSecurityLogRepository.adminSearchSecurityLog(
                        adminSearchSecurityLogFromTime, adminSearchSecurityLogToTime,
                        adminReadLogFragment(adminSearchSecurityLogUsername),
                        adminReadLogFilter(adminSearchSecurityLogEventType),
                        adminReadLogFilter(adminSearchSecurityLogResult), adminSearchSecurityLogPageRequest));
        List<AdminSearchSecurityLogRow> adminSearchSecurityLogRowList = adminSearchSecurityLogPage.stream()
                .map(this::adminReadSecurityLogRow).toList();
        return new AdminSearchSecurityLogResponse(adminSearchSecurityLogRowList,
                adminSearchSecurityLogPage.getNumber() + 1, adminCountPage(adminSearchSecurityLogPage.getTotalElements()),
                adminSearchSecurityLogPage.getTotalElements());
    }

    // ===== SECTION 5: adminSearchPickingScanHistory =====

    /**
     * What it does: Reads the requested page of the log and hides private information.
     * Input: The optional filters and the requested one-based page number.
     * Output: Safe rows in newest-first order and the page totals.
     * Placeholder: PI-08.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchPickingScanHistoryResponse adminSearchPickingScanHistory(
            Instant adminSearchPickingScanHistoryFromTime, Instant adminSearchPickingScanHistoryToTime,
            String adminSearchPickingScanHistoryOrderReference, String adminSearchPickingScanHistoryWorkerUsername,
            String adminSearchPickingScanHistoryResult, int adminSearchPickingScanHistoryPageNumber) {
        // PLACEHOLDER INPUT PI-08
        // From: Warehouse.
        // What: Picking scan rows in the shared database table.
        // How to use: Read the table directly when Warehouse starts writing its rows.
        Page<Object[]> adminSearchPickingScanHistoryPage = adminFindLogPage(adminSearchPickingScanHistoryPageNumber,
                adminSearchPickingScanHistoryPageRequest ->
                        adminLogPickingScanHistoryRepository.adminSearchPickingScanHistory(
                        adminSearchPickingScanHistoryFromTime, adminSearchPickingScanHistoryToTime,
                        adminReadLogFragment(adminSearchPickingScanHistoryOrderReference),
                        adminReadLogFragment(adminSearchPickingScanHistoryWorkerUsername),
                        adminReadLogFilter(adminSearchPickingScanHistoryResult),
                        adminSearchPickingScanHistoryPageRequest));
        List<AdminSearchPickingScanHistoryRow> adminSearchPickingScanHistoryRowList =
                adminSearchPickingScanHistoryPage.stream().map(this::adminReadPickingScanHistoryRow).toList();
        return new AdminSearchPickingScanHistoryResponse(adminSearchPickingScanHistoryRowList,
                adminSearchPickingScanHistoryPage.getNumber() + 1,
                adminCountPage(adminSearchPickingScanHistoryPage.getTotalElements()),
                adminSearchPickingScanHistoryPage.getTotalElements());
    }

    // ===== SECTION 6: adminDownloadAuditLogCSV =====

    /**
     * What it does: Exports the newest matching log rows and records the download.
     * Input: The optional search filters.
     * Output: A safe CSV attachment with a note if older matching rows were omitted.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> adminDownloadAuditLogCSV(
            Instant adminSearchAuditLogFromTime, Instant adminSearchAuditLogToTime,
            String adminSearchAuditLogActorUsername, String adminSearchAuditLogAction,
            String adminSearchAuditLogTargetType, String adminSearchAuditLogTargetID) {
        // Use the search query and projection for the full export.
        Page<Object[]> adminDownloadAuditLogCSVPage = adminLogAuditLogRepository.adminSearchAuditLog(
                        adminSearchAuditLogFromTime, adminSearchAuditLogToTime,
                        adminReadLogFragment(adminSearchAuditLogActorUsername),
                        adminReadLogFilter(adminSearchAuditLogAction),
                        adminReadLogFilter(adminSearchAuditLogTargetType),
                        adminReadLogFilter(adminSearchAuditLogTargetID),
                        PageRequest.of(0, ADMIN_CSV_MAXIMUM_ROW_COUNT));
        List<List<Object>> adminDownloadAuditLogCSVRowList = adminDownloadAuditLogCSVPage.stream()
                .map(this::adminReadAuditLogRow)
                .map(adminDownloadAuditLogCSVRow -> Arrays.<Object>asList(
                        adminDownloadAuditLogCSVRow.auditID(), adminDownloadAuditLogCSVRow.timestamp(),
                        adminDownloadAuditLogCSVRow.actorUserID(),
                        adminDownloadAuditLogCSVRow.adminSearchAuditLogActorUsername(),
                        adminDownloadAuditLogCSVRow.action(), adminDownloadAuditLogCSVRow.targetType(),
                        adminDownloadAuditLogCSVRow.targetID(), adminDownloadAuditLogCSVRow.oldValue(),
                        adminDownloadAuditLogCSVRow.newValue(), adminDownloadAuditLogCSVRow.result())).toList();

        // Record the applied filters without also placing free text with a phone number in the audit log.
        Map<String, Object> adminDownloadAuditLogCSVFilterMap = new LinkedHashMap<>();
        adminDownloadAuditLogCSVFilterMap.put("adminSearchAuditLogFromTime", adminSearchAuditLogFromTime);
        adminDownloadAuditLogCSVFilterMap.put("adminSearchAuditLogToTime", adminSearchAuditLogToTime);
        adminDownloadAuditLogCSVFilterMap.put("adminSearchAuditLogActorUsername", adminSearchAuditLogActorUsername);
        adminDownloadAuditLogCSVFilterMap.put("adminSearchAuditLogAction", adminSearchAuditLogAction);
        adminDownloadAuditLogCSVFilterMap.put("adminSearchAuditLogTargetType", adminSearchAuditLogTargetType);
        adminDownloadAuditLogCSVFilterMap.put("adminSearchAuditLogTargetID", adminSearchAuditLogTargetID);
        adminWriteCSVDownloadLog("CSV_AUDIT_LOG", adminDownloadAuditLogCSVRowList.size(),
                adminDownloadAuditLogCSVFilterMap);
        return sharedWriteCSVDownload(List.of(
                "auditID", "timestamp", "actorUserID", "adminSearchAuditLogActorUsername", "action", "targetType",
                "targetID", "oldValue", "newValue", "result"),
                adminDownloadAuditLogCSVRowList, "admin-audit-log", Instant.now(),
                adminReadCSVLimitMessage(adminDownloadAuditLogCSVPage));
    }

    // ===== SECTION 7: adminDownloadSecurityLogCSV =====

    /**
     * What it does: Exports the newest matching log rows and records the download.
     * Input: The optional search filters.
     * Output: A safe CSV attachment with a note if older matching rows were omitted.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> adminDownloadSecurityLogCSV(
            Instant adminSearchSecurityLogFromTime, Instant adminSearchSecurityLogToTime,
            String adminSearchSecurityLogUsername, String adminSearchSecurityLogEventType,
            String adminSearchSecurityLogResult) {
        // Use the search query and projection for the full export.
        Page<Object[]> adminDownloadSecurityLogCSVPage = adminLogSecurityLogRepository.adminSearchSecurityLog(
                        adminSearchSecurityLogFromTime, adminSearchSecurityLogToTime,
                        adminReadLogFragment(adminSearchSecurityLogUsername),
                        adminReadLogFilter(adminSearchSecurityLogEventType),
                        adminReadLogFilter(adminSearchSecurityLogResult),
                        PageRequest.of(0, ADMIN_CSV_MAXIMUM_ROW_COUNT));
        List<List<Object>> adminDownloadSecurityLogCSVRowList = adminDownloadSecurityLogCSVPage.stream()
                .map(this::adminReadSecurityLogRow)
                .map(adminDownloadSecurityLogCSVRow -> Arrays.<Object>asList(
                        adminDownloadSecurityLogCSVRow.securityLogID(), adminDownloadSecurityLogCSVRow.timestamp(),
                        adminDownloadSecurityLogCSVRow.userID(),
                        adminDownloadSecurityLogCSVRow.adminSearchSecurityLogUsername(),
                        adminDownloadSecurityLogCSVRow.eventType(), adminDownloadSecurityLogCSVRow.result(),
                        adminDownloadSecurityLogCSVRow.ipAddress(), adminDownloadSecurityLogCSVRow.deviceInfo(),
                        adminDownloadSecurityLogCSVRow.details())).toList();

        // Record the applied filters without also placing free text with a phone number in the audit log.
        Map<String, Object> adminDownloadSecurityLogCSVFilterMap = new LinkedHashMap<>();
        adminDownloadSecurityLogCSVFilterMap.put("adminSearchSecurityLogFromTime", adminSearchSecurityLogFromTime);
        adminDownloadSecurityLogCSVFilterMap.put("adminSearchSecurityLogToTime", adminSearchSecurityLogToTime);
        adminDownloadSecurityLogCSVFilterMap.put("adminSearchSecurityLogUsername", adminSearchSecurityLogUsername);
        adminDownloadSecurityLogCSVFilterMap.put("adminSearchSecurityLogEventType", adminSearchSecurityLogEventType);
        adminDownloadSecurityLogCSVFilterMap.put("adminSearchSecurityLogResult", adminSearchSecurityLogResult);
        adminWriteCSVDownloadLog("CSV_SECURITY_LOG", adminDownloadSecurityLogCSVRowList.size(),
                adminDownloadSecurityLogCSVFilterMap);
        return sharedWriteCSVDownload(List.of(
                "securityLogID", "timestamp", "userID", "adminSearchSecurityLogUsername", "eventType", "result",
                "ipAddress", "deviceInfo", "details"),
                adminDownloadSecurityLogCSVRowList, "admin-security-log", Instant.now(),
                adminReadCSVLimitMessage(adminDownloadSecurityLogCSVPage));
    }

    // ===== SECTION 8: adminDownloadPickingScanHistoryCSV =====

    /**
     * What it does: Exports the newest matching log rows and records the download.
     * Input: The optional search filters.
     * Output: A safe CSV attachment with a note if older matching rows were omitted.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> adminDownloadPickingScanHistoryCSV(
            Instant adminSearchPickingScanHistoryFromTime, Instant adminSearchPickingScanHistoryToTime,
            String adminSearchPickingScanHistoryOrderReference, String adminSearchPickingScanHistoryWorkerUsername,
            String adminSearchPickingScanHistoryResult) {
        // Use the search query and projection for the full export.
        Page<Object[]> adminDownloadPickingScanHistoryCSVPage =
                adminLogPickingScanHistoryRepository.adminSearchPickingScanHistory(
                        adminSearchPickingScanHistoryFromTime, adminSearchPickingScanHistoryToTime,
                        adminReadLogFragment(adminSearchPickingScanHistoryOrderReference),
                        adminReadLogFragment(adminSearchPickingScanHistoryWorkerUsername),
                        adminReadLogFilter(adminSearchPickingScanHistoryResult),
                        PageRequest.of(0, ADMIN_CSV_MAXIMUM_ROW_COUNT));
        List<List<Object>> adminDownloadPickingScanHistoryCSVRowList = adminDownloadPickingScanHistoryCSVPage.stream()
                .map(this::adminReadPickingScanHistoryRow)
                .map(adminDownloadPickingScanHistoryCSVRow -> Arrays.<Object>asList(
                        adminDownloadPickingScanHistoryCSVRow.scanID(),
                        adminDownloadPickingScanHistoryCSVRow.scanTime(),
                        adminDownloadPickingScanHistoryCSVRow.pickingTaskID(),
                        adminDownloadPickingScanHistoryCSVRow.orderID(),
                        adminDownloadPickingScanHistoryCSVRow.adminSearchPickingScanHistoryOrderReference(),
                        adminDownloadPickingScanHistoryCSVRow.workerUserID(),
                        adminDownloadPickingScanHistoryCSVRow.adminSearchPickingScanHistoryWorkerUsername(),
                        adminDownloadPickingScanHistoryCSVRow.scannedIdentifier(),
                        adminDownloadPickingScanHistoryCSVRow.result(),
                        adminDownloadPickingScanHistoryCSVRow.reason(),
                        adminDownloadPickingScanHistoryCSVRow.originalScanID(),
                        adminDownloadPickingScanHistoryCSVRow.adminSearchPickingScanHistoryProductSKU())).toList();

        // Record the applied filters without also placing free text with a phone number in the audit log.
        Map<String, Object> adminDownloadPickingScanHistoryCSVFilterMap = new LinkedHashMap<>();
        adminDownloadPickingScanHistoryCSVFilterMap.put(
                "adminSearchPickingScanHistoryFromTime", adminSearchPickingScanHistoryFromTime);
        adminDownloadPickingScanHistoryCSVFilterMap.put(
                "adminSearchPickingScanHistoryToTime", adminSearchPickingScanHistoryToTime);
        adminDownloadPickingScanHistoryCSVFilterMap.put(
                "adminSearchPickingScanHistoryOrderReference", adminSearchPickingScanHistoryOrderReference);
        adminDownloadPickingScanHistoryCSVFilterMap.put(
                "adminSearchPickingScanHistoryWorkerUsername", adminSearchPickingScanHistoryWorkerUsername);
        adminDownloadPickingScanHistoryCSVFilterMap.put(
                "adminSearchPickingScanHistoryResult", adminSearchPickingScanHistoryResult);
        adminWriteCSVDownloadLog("CSV_PICKING_SCAN_HISTORY", adminDownloadPickingScanHistoryCSVRowList.size(),
                adminDownloadPickingScanHistoryCSVFilterMap);
        return sharedWriteCSVDownload(List.of(
                "scanID", "scanTime", "pickingTaskID", "orderID", "adminSearchPickingScanHistoryOrderReference",
                "workerUserID", "adminSearchPickingScanHistoryWorkerUsername", "scannedIdentifier", "result",
                "reason", "originalScanID", "adminSearchPickingScanHistoryProductSKU"),
                adminDownloadPickingScanHistoryCSVRowList, "admin-picking-scan-history", Instant.now(),
                adminReadCSVLimitMessage(adminDownloadPickingScanHistoryCSVPage));
    }

    // ===== SECTION 9: adminViewLogCleanupStatus =====

    /**
     * What it does: Reads the most recent successful retention run.
     * Input: None.
     * Output: The latest run time and counts, or a false flag and empty time before the first run.
     */
    @Transactional(readOnly = true)
    public AdminViewLogCleanupStatusResponse adminViewLogCleanupStatus() {
        AuditLog adminViewLogCleanupStatusRow = adminLogAuditLogRepository
                .findFirstByActionOrderByTimestampDescAuditIDDesc(SHARED_AUDIT_ACTION_LOG_CLEANUP_RUN).orElse(null);
        if (adminViewLogCleanupStatusRow == null) {
            return new AdminViewLogCleanupStatusResponse(false, null, 0, 0, 0,
                    ADMIN_VIEW_LOG_CLEANUP_STATUS_KEEP_RULE_TEXT);
        }

        // The cleanup task records one number per log table.
        Map<String, Object> adminViewLogCleanupStatusCountMap = adminViewLogCleanupStatusRow.newValue;
        return new AdminViewLogCleanupStatusResponse(true, adminViewLogCleanupStatusRow.timestamp,
                adminReadLogCleanupCount(adminViewLogCleanupStatusCountMap, "securityLogDeletedCount"),
                adminReadLogCleanupCount(adminViewLogCleanupStatusCountMap, "pickingScanHistoryDeletedCount"),
                adminReadLogCleanupCount(adminViewLogCleanupStatusCountMap, "auditLogDeletedCount"),
                ADMIN_VIEW_LOG_CLEANUP_STATUS_KEEP_RULE_TEXT);
    }

    // ===== SECTION 10: HELPER FUNCTIONS =====

    /**
     * What it does: Loads the requested page or the last available page when a saved link is stale.
     * Input: A one-based page number and the query with its filters.
     * Output: The page of matches and the total size of the filtered set.
     */
    private Page<Object[]> adminFindLogPage(int adminFindLogPageNumber,
            Function<Pageable, Page<Object[]>> adminFindLogPageQuery) {
        int adminFindLogPageIndex =
                Math.clamp((long) adminFindLogPageNumber - 1, 0, Integer.MAX_VALUE / ADMIN_TABLE_PAGE_SIZE);
        Page<Object[]> adminFindLogPageResult =
                adminFindLogPageQuery.apply(PageRequest.of(adminFindLogPageIndex, ADMIN_TABLE_PAGE_SIZE));
        if (adminFindLogPageResult.getTotalElements() == 0) {
            return Page.empty(PageRequest.of(0, ADMIN_TABLE_PAGE_SIZE));
        }

        // A filter or clean-up can make a page smaller than the address requested.
        int adminFindLogPageLastIndex = adminCountPage(adminFindLogPageResult.getTotalElements()) - 1;
        if (adminFindLogPageIndex > adminFindLogPageLastIndex) {
            return adminFindLogPageQuery.apply(PageRequest.of(adminFindLogPageLastIndex, ADMIN_TABLE_PAGE_SIZE));
        }
        return adminFindLogPageResult;
    }

    /**
     * What it does: Treats blank filter fields as absent in both search and export.
     * Input: The supplied filter text, which may be missing.
     * Output: Null for a blank filter, or its literal value.
     */
    private String adminReadLogFilter(String adminReadLogFilterValue) {
        return adminReadLogFilterValue == null || adminReadLogFilterValue.isBlank() ? null : adminReadLogFilterValue;
    }

    /**
     * What it does: Gives a literal substring filter to the search query.
     * Input: Optional text used to match part of a name or order reference.
     * Output: An empty string for a blank or missing filter, or its literal value.
     */
    private String adminReadLogFragment(String adminReadLogFragmentValue) {
        return Objects.toString(adminReadLogFilter(adminReadLogFragmentValue), "");
    }

    /**
     * What it does: Makes a safe audit search row from the database projection.
     * Input: The stored audit fields and the actor's username.
     * Output: Public audit fields and masked values.
     */
    private AdminSearchAuditLogRow adminReadAuditLogRow(Object[] adminReadAuditLogRowValues) {
        return new AdminSearchAuditLogRow((Long) adminReadAuditLogRowValues[0],
                (Instant) adminReadAuditLogRowValues[1], (Long) adminReadAuditLogRowValues[2],
                Objects.toString(adminReadAuditLogRowValues[3], "system"), (String) adminReadAuditLogRowValues[4],
                (String) adminReadAuditLogRowValues[5], (String) adminReadAuditLogRowValues[6],
                adminHidePrivateValue((String) adminReadAuditLogRowValues[7]),
                adminHidePrivateValue((String) adminReadAuditLogRowValues[8]), (String) adminReadAuditLogRowValues[9]);
    }

    /**
     * What it does: Makes a safe security search row.
     * Input: The stored event and the account username, if one was recorded.
     * Output: Public event fields and masked details.
     */
    private AdminSearchSecurityLogRow adminReadSecurityLogRow(Object[] adminReadSecurityLogRowValues) {
        SecurityLog adminReadSecurityLogRowEvent = (SecurityLog) adminReadSecurityLogRowValues[0];
        return new AdminSearchSecurityLogRow(adminReadSecurityLogRowEvent.securityLogID,
                adminReadSecurityLogRowEvent.timestamp, adminReadSecurityLogRowEvent.userID,
                (String) adminReadSecurityLogRowValues[1], adminReadSecurityLogRowEvent.eventType,
                adminReadSecurityLogRowEvent.result, adminReadSecurityLogRowEvent.ipAddress,
                adminHidePrivateValue(adminReadSecurityLogRowEvent.deviceInfo),
                adminHidePrivateValue(adminReadSecurityLogRowEvent.details));
    }

    /**
     * What it does: Combines the scan fields and related order, worker and SKU.
     * Input: A scan projection that has already replaced the untrusted scanned code.
     * Output: A public scan row that also masks the reason.
     */
    private AdminSearchPickingScanHistoryRow adminReadPickingScanHistoryRow(
            Object[] adminReadPickingScanHistoryRowValues) {
        return new AdminSearchPickingScanHistoryRow((Long) adminReadPickingScanHistoryRowValues[0],
                (Instant) adminReadPickingScanHistoryRowValues[1], (Long) adminReadPickingScanHistoryRowValues[2],
                (Long) adminReadPickingScanHistoryRowValues[11],
                (String) adminReadPickingScanHistoryRowValues[3], (Long) adminReadPickingScanHistoryRowValues[4],
                (String) adminReadPickingScanHistoryRowValues[5], (String) adminReadPickingScanHistoryRowValues[6],
                (SharedScanResult) adminReadPickingScanHistoryRowValues[7],
                adminHidePrivateValue((String) adminReadPickingScanHistoryRowValues[8]),
                (Long) adminReadPickingScanHistoryRowValues[9], (String) adminReadPickingScanHistoryRowValues[10]);
    }

    /**
     * What it does: Records one completed log download with its row count and filters.
     * Input: The CSV target, exported count and supplied filters.
     * Output: One audit row in the download transaction.
     */
    private void adminWriteCSVDownloadLog(String targetID, int adminWriteCSVDownloadLogRowCount,
            Map<String, Object> adminWriteCSVDownloadLogFilterMap) {
        Map<String, Object> adminWriteCSVDownloadLogSafeFilterMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> adminWriteCSVDownloadLogFilter : adminWriteCSVDownloadLogFilterMap.entrySet()) {
            Object adminWriteCSVDownloadLogValue = adminWriteCSVDownloadLogFilter.getValue();
            if (adminWriteCSVDownloadLogValue != null && !adminWriteCSVDownloadLogValue.toString().isBlank()) {
                adminWriteCSVDownloadLogSafeFilterMap.put(adminWriteCSVDownloadLogFilter.getKey(),
                        adminHidePrivateValue(adminWriteCSVDownloadLogValue.toString()));
            }
        }
        Long actorUserID = adminLogCurrentUserReader.sharedReadCurrentUser().userID();
        adminLogAuditWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_CSV_DOWNLOADED, "SYSTEM", targetID,
                null, Map.of("rowCount", adminWriteCSVDownloadLogRowCount,
                        "filters", adminWriteCSVDownloadLogSafeFilterMap));
    }

    /**
     * What it does: Marks exports where the limit omitted older log rows.
     * Input: The export page and its total row count.
     * Output: The required footer text, or no footer when all matches were included.
     */
    private String adminReadCSVLimitMessage(Page<Object[]> adminReadCSVLimitMessagePage) {
        return adminReadCSVLimitMessagePage.getTotalElements() > ADMIN_CSV_MAXIMUM_ROW_COUNT
                ? "Only the newest 10000 rows are included." : null;
    }

    /**
     * What it does: Reads one deletion count from the saved log clean-up outcome.
     * Input: The saved values and the name of the count.
     * Output: The count, or zero if the entry does not have a number for it.
     */
    private int adminReadLogCleanupCount(Map<String, Object> adminReadLogCleanupCountMap,
            String adminReadLogCleanupCountKey) {
        if (adminReadLogCleanupCountMap == null
                || !(adminReadLogCleanupCountMap.get(adminReadLogCleanupCountKey) instanceof Number
                        adminReadLogCleanupCountValue)) {
            return 0;
        }
        return adminReadLogCleanupCountValue.intValue();
    }
}
