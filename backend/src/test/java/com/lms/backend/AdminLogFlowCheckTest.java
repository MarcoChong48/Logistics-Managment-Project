// TEMPORARY TEST CODE - remove before integration
// Stage 4 reruns the Logs acceptance cases through real HTTP and the disposable PostgreSQL database.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AdminLogFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: ADDRESSES AND FIXTURES =====
    private static final String LOGS = "/api/admin/logs";
    private static final String AUDIT = LOGS + "/audit";
    private static final String SECURITY = LOGS + "/security";
    private static final String SCANS = LOGS + "/picking-scan";
    private static final String MARKER_FILTER = "TEMPORARY_PRIVATE_VALUE_ROW";
    private static final List<String> PRIVATE_MARKER_LIST = List.of("PRIVATE-PHONE-MARKER",
            "PRIVATE-ADDRESS-MARKER", "PRIVATE-RECIPIENT-MARKER", "PRIVATE-PASSWORD-MARKER", "5550 0000 1234",
            "9123-4567");
    private String orderReference;
    private Instant adminViewLogCleanupStatusFirstRunTime;

    // ===== SECTION 2: FLOW CASES =====

    // What it does: Checks tab markup, audit columns and newest-first ordering.
    // Input: The Stage 2 database and the signed-in Admin.
    // Output: Evidence from the page and the real audit request.
    @Test @DisplayName("S3-T01")
    void checkS3T01() {
        Session logAdmin = admin();
        String adminShowLogTabPage = logAdmin.get("/admin/admin-logs.html").expect(200).body();
        for (String adminShowLogTabName : List.of("Audit log", "Security log", "Picking scans")) {
            assertTrue(adminShowLogTabPage.contains(adminShowLogTabName));
        }
        assertTrue(adminShowLogTabPage.contains("Log rows cannot be changed or deleted here."));
        assertFalse(adminShowLogTabPage.contains("data-edit"));
        assertFalse(adminShowLogTabPage.contains("data-delete"));
        List<JsonNode> adminSearchAuditLogRowList = rows(logAdmin.get(AUDIT), "adminSearchAuditLog");
        assertFalse(adminSearchAuditLogRowList.isEmpty());
        Instant adminSearchAuditLogPreviousTime = Instant.MAX;
        for (JsonNode adminSearchAuditLogRow : adminSearchAuditLogRowList) {
            Instant adminSearchAuditLogTime = Instant.parse(adminSearchAuditLogRow.path("timestamp").asText());
            assertFalse(adminSearchAuditLogTime.isAfter(adminSearchAuditLogPreviousTime));
            adminSearchAuditLogPreviousTime = adminSearchAuditLogTime;
            for (String adminSearchAuditLogColumn : List.of("adminSearchAuditLogActorUsername", "action",
                    "targetType", "targetID", "oldValue", "newValue")) {
                assertTrue(adminSearchAuditLogRow.has(adminSearchAuditLogColumn), adminSearchAuditLogColumn);
            }
        }
        reportPartial("Tab-strip motion, keyboard focus, local-time display and visual absence of edit controls.");
    }

    // What it does: Checks the action filter, combined actor filter and cleared results.
    // Input: Bans created in the user flows.
    // Output: Filtered rows and unfiltered count.
    @Test @DisplayName("S3-T02")
    void checkS3T02() {
        Session logAdmin = admin();
        String adminSearchAuditLogBanPath = AUDIT + "?adminSearchAuditLogAction=USER_BANNED";
        List<JsonNode> adminSearchAuditLogBanList = rows(logAdmin.get(adminSearchAuditLogBanPath), "adminSearchAuditLog");
        assertFalse(adminSearchAuditLogBanList.isEmpty());
        assertTrue(adminSearchAuditLogBanList.stream().allMatch(row -> "USER_BANNED".equals(row.path("action").asText())));
        String adminSearchAuditLogActorPath = adminSearchAuditLogBanPath + "&adminSearchAuditLogActorUsername="
                + java.net.URLEncoder.encode(bootstrapUsername, java.nio.charset.StandardCharsets.UTF_8);
        Reply adminSearchAuditLogActorReply = logAdmin.get(adminSearchAuditLogActorPath).expect(200);
        List<JsonNode> adminSearchAuditLogActorRowList = rows(adminSearchAuditLogActorReply, "adminSearchAuditLog");
        assertFalse(adminSearchAuditLogActorRowList.isEmpty());
        assertTrue(adminSearchAuditLogActorRowList.stream().allMatch(row -> bootstrapUsername.equalsIgnoreCase(
                row.path("adminSearchAuditLogActorUsername").asText())));
        assertEquals(adminSearchAuditLogActorReply.json(), logAdmin.get(adminSearchAuditLogActorPath).expect(200).json());
        assertTrue(logAdmin.get(AUDIT).expect(200).json().path("adminSearchAuditLogTotalRowCount").asLong()
                > adminSearchAuditLogActorReply.json().path("adminSearchAuditLogTotalRowCount").asLong());
        reportPartial("Browser address and tab controls persisting across reload.");
    }

    // What it does: Searches login failures and combines the event with a username filter.
    // Input: Prior rejected sign-ins.
    // Output: Failure rows with request IP and browser text.
    @Test @DisplayName("S3-T03")
    void checkS3T03() {
        Session logAdmin = admin();
        String adminSearchSecurityLogFailurePath = SECURITY + "?adminSearchSecurityLogEventType=LOGIN_FAILURE";
        List<JsonNode> adminSearchSecurityLogFailureList = rows(logAdmin.get(adminSearchSecurityLogFailurePath),
                "adminSearchSecurityLog");
        assertFalse(adminSearchSecurityLogFailureList.isEmpty());
        assertTrue(adminSearchSecurityLogFailureList.stream().allMatch(row ->
                "LOGIN_FAILURE".equals(row.path("eventType").asText()) && !row.path("ipAddress").asText().isBlank()
                && !row.path("deviceInfo").asText().isBlank()));
        List<JsonNode> adminSearchSecurityLogDriverList = rows(
                logAdmin.get(adminSearchSecurityLogFailurePath + "&adminSearchSecurityLogUsername=ken_driver"),
                "adminSearchSecurityLog");
        assertFalse(adminSearchSecurityLogDriverList.isEmpty());
        assertTrue(adminSearchSecurityLogDriverList.stream().allMatch(row ->
                "LOGIN_FAILURE".equals(row.path("eventType").asText())
                && "ken_driver".equals(row.path("adminSearchSecurityLogUsername").asText())));
    }

    // What it does: Creates scan history and checks counts, hidden values and the reversal link.
    // Input: The test-mode picking-scan tool.
    // Output: Six scoped rows and the order reference used by the CSV case.
    @Test @DisplayName("S3-T04")
    void checkS3T04() {
        Session logAdmin = admin();
        String adminTemporaryCreatePickingScanRowMessage = logAdmin.tool("picking-scan-rows", null).expect(200)
                .json().path("message").asText();
        var adminTemporaryCreatePickingScanRowReference =
                Pattern.compile("TEST-[0-9]+").matcher(adminTemporaryCreatePickingScanRowMessage);
        assertTrue(adminTemporaryCreatePickingScanRowReference.find());
        orderReference = adminTemporaryCreatePickingScanRowReference.group();
        Reply adminSearchPickingScanHistoryReply = logAdmin.get(pickingScanPath(SCANS)).expect(200);
        List<JsonNode> adminSearchPickingScanHistoryRowList = rows(adminSearchPickingScanHistoryReply,
                "adminSearchPickingScanHistory");
        assertEquals(6, adminSearchPickingScanHistoryRowList.size());
        assertPrivateMarkersHidden(adminSearchPickingScanHistoryReply.body());
        long adminSearchPickingScanHistoryPenScanID = 0;
        int adminSearchPickingScanHistoryAcceptedMugCount = 0;
        int adminSearchPickingScanHistoryAcceptedPenCount = 0;
        var adminSearchPickingScanHistoryRejectReasonList = new ArrayList<String>();
        JsonNode adminSearchPickingScanHistoryReversalRow = null;
        for (JsonNode adminSearchPickingScanHistoryRow : adminSearchPickingScanHistoryRowList) {
            String SKU = adminSearchPickingScanHistoryRow.path("adminSearchPickingScanHistoryProductSKU").asText("");
            String adminSearchPickingScanHistoryScanned = adminSearchPickingScanHistoryRow.path("scannedIdentifier")
                    .asText();
            String reason = adminSearchPickingScanHistoryRow.path("reason").asText();
            switch (adminSearchPickingScanHistoryRow.path("result").asText()) {
                case "ACCEPTED" -> {
                    assertEquals(SKU, adminSearchPickingScanHistoryScanned);
                    if (SKU.startsWith("TEST-MUG-")) {
                        adminSearchPickingScanHistoryAcceptedMugCount++;
                    } else {
                        assertTrue(SKU.startsWith("TEST-PEN-"));
                        adminSearchPickingScanHistoryAcceptedPenCount++;
                        adminSearchPickingScanHistoryPenScanID = adminSearchPickingScanHistoryRow.path("scanID").asLong();
                    }
                }
                case "REJECTED" -> {
                    assertEquals("[unknown code]", adminSearchPickingScanHistoryScanned);
                    assertTrue(SKU.isEmpty());
                    adminSearchPickingScanHistoryRejectReasonList.add(reason);
                }
                case "REVERSED" -> {
                    assertNull(adminSearchPickingScanHistoryReversalRow);
                    assertEquals("picked by mistake", reason);
                    assertTrue(SKU.startsWith("TEST-PEN-"));
                    assertEquals(SKU, adminSearchPickingScanHistoryScanned);
                    adminSearchPickingScanHistoryReversalRow = adminSearchPickingScanHistoryRow;
                }
                default -> fail("Unexpected scan result.");
            }
        }
        assertEquals(2, adminSearchPickingScanHistoryAcceptedMugCount);
        assertEquals(1, adminSearchPickingScanHistoryAcceptedPenCount);
        assertEquals(2, adminSearchPickingScanHistoryRejectReasonList.size());
        assertTrue(adminSearchPickingScanHistoryRejectReasonList.containsAll(
                List.of("not part of this order, call [hidden number]", "=1+1")));
        assertNotNull(adminSearchPickingScanHistoryReversalRow);
        assertEquals(adminSearchPickingScanHistoryPenScanID,
                adminSearchPickingScanHistoryReversalRow.path("originalScanID").asLong());
    }

    // What it does: Verifies the audit log cannot be deleted through the live route.
    // Input: The signed-in Admin's DELETE request and an audit row count.
    // Output: The refusal and preserved count.
    @Test @DisplayName("S3-T05")
    void checkS3T05() {
        Session logAdmin = admin();
        long adminSearchAuditLogBeforeCount = count("SELECT count(*) FROM audit_log");
        Reply adminSearchAuditLogDelete = logAdmin.send("DELETE", AUDIT + "/1", null, true);
        assertTrue(List.of(403, 404, 405).contains(adminSearchAuditLogDelete.response().statusCode()));
        assertEquals(adminSearchAuditLogBeforeCount, count("SELECT count(*) FROM audit_log"));
    }

    // What it does: Verifies audit and security log results hide the marked private values.
    // Input: The private-value fixture tool.
    // Output: Both masked rows and no raw markers in either response.
    @Test @DisplayName("S3-T06")
    void checkS3T06() {
        Session logAdmin = admin();
        logAdmin.tool("private-value-log-rows", null).expect(200);
        Reply adminSearchAuditLogPrivateReply = logAdmin.get(AUDIT + "?adminSearchAuditLogAction=" + MARKER_FILTER)
                .expect(200);
        Reply adminSearchSecurityLogPrivateReply = logAdmin.get(SECURITY + "?adminSearchSecurityLogEventType="
                + MARKER_FILTER).expect(200);
        String adminSearchAuditLogPrivateValue = rows(adminSearchAuditLogPrivateReply, "adminSearchAuditLog")
                .getFirst().path("newValue").asText();
        assertPrivateAuditValue(adminSearchAuditLogPrivateValue);
        assertEquals("call [hidden number] or [hidden number] about order TEST-0001 on 2026-11-10",
                rows(adminSearchSecurityLogPrivateReply, "adminSearchSecurityLog").getFirst().path("details").asText());
        assertPrivateMarkersHidden(adminSearchAuditLogPrivateReply.body());
        assertPrivateMarkersHidden(adminSearchSecurityLogPrivateReply.body());
    }

    // What it does: Downloads the scoped user-created audit rows and checks columns and count.
    // Input: The USER_CREATED audit filter.
    // Output: A UTF-8 CSV whose data agrees with the search.
    @Test @DisplayName("S3-T07")
    void checkS3T07() {
        Session logAdmin = admin();
        String adminSearchAuditLogFilter = "?adminSearchAuditLogAction=USER_CREATED";
        long adminSearchAuditLogCreatedCount = logAdmin.get(AUDIT + adminSearchAuditLogFilter).expect(200).json()
                .path("adminSearchAuditLogTotalRowCount").asLong();
        assertTrue(adminSearchAuditLogCreatedCount > 0);
        String adminDownloadAuditLogCSVText = csv(logAdmin.get(AUDIT + "/csv" + adminSearchAuditLogFilter), "audit-log");
        assertEquals("auditID,timestamp,actorUserID,adminSearchAuditLogActorUsername,action,targetType,targetID,"
                + "oldValue,newValue,result", adminDownloadAuditLogCSVText.lines().findFirst().orElseThrow()
                .replace("\"", ""));
        List<String> adminDownloadAuditLogCSVRowList = adminDownloadAuditLogCSVText.lines().skip(1).toList();
        assertEquals(adminSearchAuditLogCreatedCount, adminDownloadAuditLogCSVRowList.size());
        assertTrue(adminDownloadAuditLogCSVRowList.stream().allMatch(row -> row.contains(",\"USER_CREATED\",")));
        reportPartial("Opening the CSV in a spreadsheet program.");
    }

    // What it does: Checks the three unfiltered or order-filtered CSV exports for masking and formula protection.
    // Input: The prior private-value and picking-scan fixtures.
    // Output: The expected six scan rows and masked audit and security fields in the downloaded files.
    @Test @DisplayName("S3-T08")
    void checkS3T08() {
        Session logAdmin = admin();
        String adminDownloadAuditLogCSVText = csv(logAdmin.get(AUDIT + "/csv"), "audit-log");
        String adminDownloadSecurityLogCSVText = csv(logAdmin.get(SECURITY + "/csv"), "security-log");
        String adminDownloadPickingScanHistoryCSVText = csv(logAdmin.get(pickingScanPath(SCANS + "/csv")),
                "picking-scan-history");
        for (String adminDownloadLogCSVText : List.of(adminDownloadAuditLogCSVText, adminDownloadSecurityLogCSVText,
                adminDownloadPickingScanHistoryCSVText)) {
            assertPrivateMarkersHidden(adminDownloadLogCSVText);
        }
        String adminDownloadAuditLogCSVMarkerRow = adminDownloadAuditLogCSVText.lines()
                .filter(row -> row.contains(",\"" + MARKER_FILTER + "\",")).findFirst().orElseThrow();
        assertPrivateAuditValue(adminDownloadAuditLogCSVMarkerRow.replace("\"\"", "\""));
        assertTrue(adminDownloadSecurityLogCSVText.lines().anyMatch(row -> row.contains(",\"" + MARKER_FILTER + "\",")
                && row.contains("call [hidden number] or [hidden number] about order TEST-0001 on 2026-11-10")));
        List<String> adminDownloadPickingScanHistoryCSVRowList = adminDownloadPickingScanHistoryCSVText.lines()
                .skip(1).toList();
        assertEquals(6, adminDownloadPickingScanHistoryCSVRowList.size());
        assertEquals(3, adminDownloadPickingScanHistoryCSVRowList.stream()
                .filter(row -> row.contains(",\"ACCEPTED\",")).count());
        assertEquals(2, adminDownloadPickingScanHistoryCSVRowList.stream()
                .filter(row -> row.contains(",\"[unknown code]\",\"REJECTED\",")).count());
        assertEquals(1, adminDownloadPickingScanHistoryCSVRowList.stream()
                .filter(row -> row.contains(",\"REVERSED\",\"picked by mistake\",")).count());
        assertTrue(adminDownloadPickingScanHistoryCSVRowList.stream()
                .anyMatch(row -> row.contains(",\"REJECTED\",\"'=1+1\",")));
        reportPartial("Spreadsheet rendering and confirmation the formula remains text.");
    }

    // What it does: Checks the Users file exposes account columns without passwords or hashes.
    // Input: The Users CSV route.
    // Output: The named download and its header.
    @Test @DisplayName("S3-T09")
    void checkS3T09() {
        String adminDownloadUserCSVText = csv(admin().get(USERS + "/csv"), "users");
        List<String> adminDownloadUserCSVHeaderList = List.of(
                adminDownloadUserCSVText.lines().findFirst().orElseThrow().replace("\"", "").split(","));
        assertTrue(adminDownloadUserCSVHeaderList.containsAll(List.of("userID", "username", "role", "accountStatus",
                "failedLoginCount", "lockUntil", "createdTime", "adminSearchUserLastLoginTime")));
        assertFalse(adminDownloadUserCSVHeaderList.stream().anyMatch(column ->
                column.equalsIgnoreCase("password") || column.equalsIgnoreCase("passwordHash")));
        assertFalse(adminDownloadUserCSVText.contains(FLOW_ADMIN_PASSWORD));
        reportPartial("User-interface download initiation.");
    }

    // What it does: Confirms each export in the earlier cases writes its row count and filters to the audit log.
    // Input: The CSV_DOWNLOADED audit filter.
    // Output: Five named download rows from the prior cases.
    @Test @DisplayName("S3-T10")
    void checkS3T10() {
        List<JsonNode> adminSearchAuditLogDownloadRowList = rows(
                admin().get(AUDIT + "?adminSearchAuditLogAction=CSV_DOWNLOADED"), "adminSearchAuditLog");
        var adminSearchAuditLogDownloadTargetList = new ArrayList<String>();
        for (JsonNode adminSearchAuditLogRow : adminSearchAuditLogDownloadRowList) {
            String newValue = adminSearchAuditLogRow.path("newValue").asText();
            assertEquals("CSV_DOWNLOADED", adminSearchAuditLogRow.path("action").asText());
            assertTrue(newValue.contains("\"rowCount\""));
            assertTrue(newValue.contains("\"filters\""));
            adminSearchAuditLogDownloadTargetList.add(adminSearchAuditLogRow.path("targetID").asText());
        }
        assertTrue(adminSearchAuditLogDownloadTargetList.containsAll(
                List.of("CSV_AUDIT_LOG", "CSV_SECURITY_LOG", "CSV_PICKING_SCAN_HISTORY", "CSV_USERS")));
        assertTrue(adminSearchAuditLogDownloadRowList.stream().anyMatch(row -> row.path("newValue").asText()
                .contains("USER_CREATED")));
        assertTrue(adminSearchAuditLogDownloadRowList.stream().anyMatch(row -> row.path("newValue").asText()
                .contains(orderReference)));
    }

    // What it does: Inserts retention fixtures and checks the first cleanup deletes only eligible rows.
    // Input: The old-row and cleanup test tools.
    // Output: Exact before and after counts and a current run status.
    @Test @DisplayName("S3-T11")
    void checkS3T11() {
        Session logAdmin = admin();
        logAdmin.tool("old-log-rows", null).expect(200);
        assertEquals(List.of(4L, 5L, 2L), oldLogCounts());
        logAdmin.tool("run-log-cleanup", null).expect(200);
        assertEquals(List.of(2L, 3L, 1L), oldLogCounts());
        adminViewLogCleanupStatusFirstRunTime = assertCleanupStatus(logAdmin, 2, 2, 1);
        Instant adminViewLogCleanupStatusCurrentTime = Instant.now();
        assertFalse(adminViewLogCleanupStatusFirstRunTime.isBefore(adminViewLogCleanupStatusCurrentTime.minusSeconds(30)));
        assertFalse(adminViewLogCleanupStatusFirstRunTime.isAfter(adminViewLogCleanupStatusCurrentTime.plusSeconds(1)));
    }

    // What it does: Repeats cleanup and checks that a later run records no additional deletions.
    // Input: The fixtures that remain after the first cleanup.
    // Output: Unchanged fixture counts and a newer zero-deletion run.
    @Test @DisplayName("S3-T12")
    void checkS3T12() {
        Session logAdmin = admin();
        logAdmin.tool("run-log-cleanup", null).expect(200);
        assertEquals(List.of(2L, 3L, 1L), oldLogCounts());
        assertTrue(assertCleanupStatus(logAdmin, 0, 0, 0).isAfter(adminViewLogCleanupStatusFirstRunTime));
    }

    // What it does: Records the scheduled restart case for manual verification.
    // Input: None. Output: The reason the Compose restart and wait are skipped.
    @Test @DisplayName("S3-T13") @Disabled("Requires restarting the live backend and waiting six minutes.")
    void checkS3T13() {}

    // What it does: Reads adjacent security-log pages and checks the 20-row limit and page ordering.
    // Input: Prior flow activity.
    // Output: Correct counts and distinct pages.
    @Test @DisplayName("S3-T14")
    void checkS3T14() {
        Session logAdmin = admin();
        Reply adminSearchSecurityLogFirstReply = logAdmin.get(SECURITY + "?adminSearchSecurityLogPageNumber=1");
        Reply adminSearchSecurityLogSecondReply = logAdmin.get(SECURITY + "?adminSearchSecurityLogPageNumber=2");
        List<JsonNode> adminSearchSecurityLogFirstRowList = rows(adminSearchSecurityLogFirstReply,
                "adminSearchSecurityLog");
        List<JsonNode> adminSearchSecurityLogSecondRowList = rows(adminSearchSecurityLogSecondReply,
                "adminSearchSecurityLog");
        assertEquals(20, adminSearchSecurityLogFirstRowList.size());
        assertFalse(adminSearchSecurityLogSecondRowList.isEmpty());
        assertTrue(adminSearchSecurityLogSecondRowList.size() <= 20);
        assertEquals(2, adminSearchSecurityLogSecondReply.json().path("adminSearchSecurityLogPageNumber").asInt());
        assertTrue(adminSearchSecurityLogFirstRowList.stream().noneMatch(first -> adminSearchSecurityLogSecondRowList
                .stream().anyMatch(second -> first.path("securityLogID").equals(second.path("securityLogID")))));
    }

    // What it does: Verifies a future cutoff hides everything and today's interval includes today's rows.
    // Input: The current local date represented as UTC instants.
    // Output: Empty and date-limited real responses.
    @Test @DisplayName("S3-T15")
    void checkS3T15() {
        Session logAdmin = admin();
        Instant adminSearchAuditLogNow = Instant.now();
        Instant adminSearchAuditLogStartOfDay = adminSearchAuditLogNow.atZone(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.DAYS).toInstant();
        assertTrue(rows(logAdmin.get(AUDIT + "?adminSearchAuditLogFromTime=" + adminSearchAuditLogNow.plusSeconds(60)),
                "adminSearchAuditLog").isEmpty());
        List<JsonNode> adminSearchAuditLogTodayRowList = rows(logAdmin.get(AUDIT + "?adminSearchAuditLogFromTime="
                + adminSearchAuditLogStartOfDay + "&adminSearchAuditLogToTime=" + adminSearchAuditLogNow),
                "adminSearchAuditLog");
        assertFalse(adminSearchAuditLogTodayRowList.isEmpty());
        assertTrue(adminSearchAuditLogTodayRowList.stream().allMatch(row -> {
            Instant timestamp = Instant.parse(row.path("timestamp").asText());
            return !timestamp.isBefore(adminSearchAuditLogStartOfDay) && !timestamp.isAfter(adminSearchAuditLogNow);
        }));
        reportPartial("Browser's datetime picker and displayed local times.");
    }

    // What it does: Confirms a signed-in worker cannot read the audit route.
    // Input: The permanent worker account established by Stage 2.
    // Output: The live role denial response.
    @Test @DisplayName("S3-T16")
    void checkS3T16() {
        Session worker = browser();
        worker.login("mei_picker", FLOW_PICKER_PASSWORD).expect(200);
        worker.get(AUDIT).expect(403).code("SHARED_ACCESS_DENIED");
    }

    // ===== SECTION 3: VERIFICATION HELPERS =====

    // What it does: Unpacks a successful search's rows.
    // Input: An HTTP response and its function name. Output: A list of rows.
    private List<JsonNode> rows(Reply searchReply, String searchFunctionName) {
        JsonNode searchRowList = searchReply.expect(200).json().path(searchFunctionName + "RowList");
        assertTrue(searchRowList.isArray());
        var searchResultList = new ArrayList<JsonNode>();
        searchRowList.forEach(searchResultList::add);
        return searchResultList;
    }

    // What it does: Limits one scan search or download to the created order.
    // Input: The API path. Output: A path with the order filter.
    private String pickingScanPath(String pickingScanPath) {
        assertNotNull(orderReference, "The picking-scan fixture must be created first.");
        return pickingScanPath + "?adminSearchPickingScanHistoryOrderReference=" + orderReference;
    }

    // What it does: Checks a CSV response and strips the UTF-8 marker for text comparisons.
    // Input: The HTTP response and filename stem. Output: The header and remaining CSV text.
    private String csv(Reply downloadReply, String tableName) {
        assertTrue(downloadReply.expect(200).header("Content-Type").startsWith("text/csv"));
        assertTrue(downloadReply.header("Content-Disposition").matches(
                ".*admin-" + tableName + "-\\d{8}-\\d{6}\\.csv.*"));
        String csvText = downloadReply.body();
        assertTrue(csvText.startsWith("\uFEFF"));
        return csvText.substring(1);
    }

    // What it does: Checks all four tagged keys and the free-text note are masked.
    // Input: Masked audit JSON text. Output: Assertions that do not echo any private values.
    private void assertPrivateAuditValue(String auditValue) {
        for (String key : List.of("phone", "address", "recipientName", "passwordHash")) {
            assertTrue(Pattern.compile("\"" + key + "\"\\s*:\\s*\"\\[hidden]\"").matcher(auditValue).find(), key);
        }
        assertTrue(auditValue.contains("call [hidden number]"));
    }

    // What it does: Checks that known private markers never leave the live endpoints.
    // Input: An HTTP response as text. Output: A privacy assertion without logging the response.
    private void assertPrivateMarkersHidden(String responseText) {
        for (String marker : PRIVATE_MARKER_LIST) {
            assertFalse(responseText.contains(marker), "A tagged private fixture was visible.");
        }
    }

    // What it does: Counts the tagged security, scan and audit fixtures before or after cleanup.
    // Input: The flow database. Output: The three counts in that order.
    private List<Long> oldLogCounts() {
        return database.sql("""
                SELECT (SELECT count(*) FROM security_log WHERE details = 'TEMPORARY-OLD-ROW') AS security,
                       (SELECT count(*) FROM picking_scan_history WHERE reason = 'TEMPORARY-OLD-ROW') AS scan,
                       (SELECT count(*) FROM audit_log WHERE action = 'TEMPORARY_OLD_ROW') AS audit
                """).query((rows, rowNumber) -> List.of(rows.getLong("security"), rows.getLong("scan"),
                        rows.getLong("audit"))).single();
    }

    // What it does: Checks the latest cleanup status and returns its timestamp for the next check.
    // Input: The Admin session and expected counts. Output: The recorded cleanup time.
    private Instant assertCleanupStatus(Session logAdmin, int securityCount, int scanCount, int auditCount) {
        JsonNode status = logAdmin.get(LOGS + "/cleanup-status").expect(200).json();
        assertTrue(status.path("adminViewLogCleanupStatusHasRun").asBoolean());
        assertEquals(securityCount, status.path("adminViewLogCleanupStatusSecurityLogDeletedCount").asInt());
        assertEquals(scanCount, status.path("adminViewLogCleanupStatusPickingScanHistoryDeletedCount").asInt());
        assertEquals(auditCount, status.path("adminViewLogCleanupStatusAuditLogDeletedCount").asInt());
        return Instant.parse(status.path("adminViewLogCleanupStatusLastRunTime").asText());
    }
}
