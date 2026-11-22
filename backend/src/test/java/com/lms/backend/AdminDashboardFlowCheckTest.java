// TEMPORARY TEST CODE - remove before integration
// Stage 9 verifies the dashboard and counters through the live web addresses.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

import com.lms.backend.shared.value.SharedOrderStatus;

import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminDashboardFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: ROUTES AND FIXTURES =====

    private static final String ADMIN_DASHBOARD_DATA = "/api/admin/dashboard";
    private static final String ADMIN_MENU_COUNTERS = "/api/admin/menu-counters";
    private static final String ADMIN_DASHBOARD_PREFIX = "adminViewDashboard";
    private static final String ADMIN_MENU_COUNTER_PREFIX = "adminViewMenuCounter";
    private static final Pattern ADMIN_DASHBOARD_SHORTAGE_MESSAGE =
            Pattern.compile("^Created shortage case (\\d+) for order TEST-\\d+\\.$");
    private static final Pattern ADMIN_DASHBOARD_REFUND_MESSAGE =
            Pattern.compile("^Created order TEST-\\d+, refund (\\d+), payment \\d+\\.$");

    private Session adminDashboardFlowSession;

    // ===== SECTION 2: DASHBOARD FLOWS =====

    // What it does: Loads the page, the dashboard API twice and the independent health check.
    // Input: The signed-in Admin on the full flow database.
    // Output: The dashboard panels, the saved active order total and healthy database.
    @Test @Order(1) @DisplayName("S9-T01")
    void checkS9T01() {
        adminDashboardFlowSession = admin();
        String adminCheckDashboardMarkup = adminDashboardFlowSession.get(ADMIN_DASHBOARD).expect(200).body();
        for (String adminCheckDashboardPanel : List.of("adminViewDashboardActiveOrderPanel",
                "adminViewDashboardLowStockPanel", "adminViewDashboardOpenCasePanel", "sharedCheckSystemHealthPanel",
                "adminViewDashboardRefreshButton", "adminViewDashboardLastLoadedTimeText")) {
            assertTrue(adminCheckDashboardMarkup.contains("id=\"" + adminCheckDashboardPanel + "\""),
                    adminCheckDashboardPanel);
        }
        JsonNode adminCheckDashboardFirstLoad = adminReadDashboardFlowData();
        assertEquals(adminCheckDashboardFirstLoad, adminReadDashboardFlowData());
        assertEquals(count("""
                SELECT COUNT(*) FROM customer_order WHERE order_status NOT IN ('DELIVERED', 'CANCELLED')
                """), adminCheckDashboardFirstLoad.path("adminViewDashboardActiveOrderCount").asLong());
        JsonNode adminCheckDashboardHealth = adminDashboardFlowSession.get("/api/shared/health").expect(200).json();
        assertTrue(adminCheckDashboardHealth.path("sharedCheckSystemHealthDatabaseIsConnected").asBoolean());
        assertEquals(27, adminCheckDashboardHealth.path("sharedCheckSystemHealthTableCount").asInt());
        reportPartial("Refreshing from the page and seeing the last-loaded time and the four boxes in the browser.");
    }

    // What it does: Compares every order status line with the total at its linked list.
    // Input: One sample order per status, plus the earlier flow fixtures.
    // Output: All ten count entries in order, with exact agreement with the filtered Orders API.
    @Test @Order(2) @DisplayName("S9-T02")
    void checkS9T02() {
        adminDashboardFlowSession.tool("sample-order-set", null).expect(200);
        JsonNode adminCheckOrderStatusCountList = adminReadDashboardFlowData()
                .path("adminViewDashboardOrderStatusCountList");
        assertEquals(SharedOrderStatus.values().length, adminCheckOrderStatusCountList.size());
        int adminCheckOrderStatusIndex = 0;
        for (SharedOrderStatus orderStatus : SharedOrderStatus.values()) {
            JsonNode adminCheckOrderStatusCount = adminCheckOrderStatusCountList.get(adminCheckOrderStatusIndex++);
            assertEquals(orderStatus.name(), adminCheckOrderStatusCount.path("orderStatus").asText());
            JsonNode adminCheckDashboardOrderList = adminDashboardFlowSession
                    .get("/api/admin/orders?adminSearchOrderOrderStatus=" + orderStatus.name()).expect(200).json();
            assertEquals(adminCheckDashboardOrderList.path("adminSearchOrderTotalRowCount").asLong(),
                    adminCheckOrderStatusCount.path("adminViewDashboardOrderCount").asLong(), orderStatus.name());
        }
        reportPartial("Clicking an order status and reading the applied filter and count in the browser.");
    }

    // What it does: Compares all eight linked totals and verifies that a refund retry counts only the new attempt.
    // Input: The linked list filters and a failed refund with a confirmed provider failure.
    // Output: The same dashboard and list totals before and after the older refund is replaced.
    @Test @Order(3) @DisplayName("S9-T03")
    void checkS9T03() {
        long adminCheckRefundCountBefore = adminReadDashboardFlowData()
                .path("adminViewDashboardUnresolvedRefundCount").asLong();
        long refundID = adminCreateDashboardRefund("FAILED");
        assertEquals(adminCheckRefundCountBefore + 1, adminReadDashboardFlowData()
                .path("adminViewDashboardUnresolvedRefundCount").asLong());
        adminDashboardFlowSession.tool("provider-answer", Map.of(
                "adminTemporarySetProviderAnswerRefundID", refundID,
                "adminTemporarySetProviderAnswerOutcome", "FAILED")).expect(200);
        JsonNode adminCheckDashboardReplacement = adminDashboardFlowSession.post("/api/admin/refunds/" + refundID
                + "/retry", Map.of("adminRetryRefundReason", "The payment provider confirmed the failure"))
                .expect(200).json();
        assertTrue(adminCheckDashboardReplacement.path("refundID").asLong() > refundID);

        JsonNode adminCheckLinkedDashboard = adminReadDashboardFlowData();
        assertEquals(adminCheckRefundCountBefore + 1,
                adminCheckLinkedDashboard.path("adminViewDashboardUnresolvedRefundCount").asLong());
        adminCheckDashboardListCount(adminCheckLinkedDashboard, "LowStockProductCount",
                "/api/admin/products?adminSearchProductIsLowStockOnly=true", "adminSearchProduct");
        adminCheckDashboardListCount(adminCheckLinkedDashboard, "OpenShortageCaseCount",
                "/api/admin/shortage-cases?adminSearchShortageCaseCaseStatus=OPEN", "adminSearchShortageCase");
        adminCheckDashboardListCount(adminCheckLinkedDashboard, "WaitingShortageCaseCount",
                "/api/admin/shortage-cases?adminSearchShortageCaseCaseStatus=WAITING_RESTOCK",
                "adminSearchShortageCase");
        adminCheckDashboardListCount(adminCheckLinkedDashboard, "PendingClientInstructionCount",
                "/api/admin/failed-delivery-cases?adminSearchFailedDeliveryCaseFilter=NEEDS_DECISION",
                "adminSearchFailedDeliveryCase");
        adminCheckDashboardListCount(adminCheckLinkedDashboard, "WaitingClientCaseCount",
                "/api/admin/failed-delivery-cases?adminSearchFailedDeliveryCaseFilter=NO_CLIENT_REQUEST",
                "adminSearchFailedDeliveryCase");
        adminCheckDashboardListCount(adminCheckLinkedDashboard, "FlaggedDeliveryCaseCount",
                "/api/admin/failed-delivery-cases?adminSearchFailedDeliveryCaseFilter=FLAGGED",
                "adminSearchFailedDeliveryCase");
        adminCheckDashboardListCount(adminCheckLinkedDashboard, "UnresolvedRefundCount",
                "/api/admin/refunds?adminSearchRefundIsUnresolvedOnly=true", "adminSearchRefund");
        adminCheckDashboardListCount(adminCheckLinkedDashboard, "LockedUserCount",
                USERS + "?adminSearchUserAccountStatus=LOCKED_NOW", "adminSearchUser");
        reportPartial("Clicking the dashboard links and reading their applied filters and totals in the browser.");
    }

    // What it does: Tracks a new shortage from open work to waiting for stock.
    // Input: A newly reported shortage on a Picking order.
    // Output: Counts gain one open case, then move the case into waiting stock.
    @Test @Order(4) @DisplayName("S9-T04")
    void checkS9T04() {
        JsonNode adminCheckShortageBefore = adminReadDashboardFlowData();
        long shortageCaseID = adminCreateDashboardShortage();
        JsonNode adminCheckShortageOpen = adminReadDashboardFlowData();
        adminCheckDashboardCountChange(adminCheckShortageBefore, adminCheckShortageOpen, "OpenShortageCaseCount", 1);
        adminCheckDashboardCountChange(adminCheckShortageBefore, adminCheckShortageOpen, "WaitingShortageCaseCount", 0);
        adminCheckDashboardCountChange(adminCheckShortageBefore, adminCheckShortageOpen, "OpenCaseTotalCount", 1);

        adminWaitDashboardShortage(shortageCaseID);
        JsonNode adminCheckShortageWaiting = adminReadDashboardFlowData();
        adminCheckDashboardCountChange(adminCheckShortageOpen, adminCheckShortageWaiting, "OpenShortageCaseCount", -1);
        adminCheckDashboardCountChange(adminCheckShortageOpen, adminCheckShortageWaiting,
                "WaitingShortageCaseCount", 1);
        adminCheckDashboardCountChange(adminCheckShortageOpen, adminCheckShortageWaiting, "OpenCaseTotalCount", -1);
        reportPartial("Pressing the dashboard Refresh button and seeing the changed numbers.");
    }

    // What it does: Moves a listed low-stock product above its threshold.
    // Input: A new product, followed by a stock correction through Test tools.
    // Output: The low-stock total falls by one and the product leaves the box.
    @Test @Order(5) @DisplayName("S9-T05")
    void checkS9T05() {
        String SKU = "000-STAGE9-LOW";
        adminDashboardFlowSession.post("/api/admin/products", Map.of(
                "adminCreateProductSKU", SKU, "adminCreateProductName", "Dashboard low stock",
                "adminCreateProductCurrentPrice", 10, "adminCreateProductLowStockThreshold", 5,
                "adminCreateProductListingStatus", "ACTIVE")).expect(200);
        JsonNode adminCheckLowStockBefore = adminReadDashboardFlowData();
        assertTrue(adminReadDashboardStockSKUList(adminCheckLowStockBefore).contains(SKU));

        adminDashboardFlowSession.tool("product-stock", Map.of(
                "adminTemporarySetProductStockSKU", SKU, "adminTemporarySetProductStockPhysicalStock", 100,
                "adminTemporarySetProductStockReservedStock", 0)).expect(200);
        JsonNode adminCheckLowStockAfter = adminReadDashboardFlowData();
        adminCheckDashboardCountChange(adminCheckLowStockBefore, adminCheckLowStockAfter, "LowStockProductCount", -1);
        assertFalse(adminReadDashboardStockSKUList(adminCheckLowStockAfter).contains(SKU));
        reportPartial("Seeing the changed product list after pressing Refresh in the browser.");
    }

    // What it does: Locks a new test user through failed logins and checks the dashboard link target.
    // Input: Five wrong passwords from a separate cookie jar.
    // Output: One more locked user and a result row for that username in the linked list.
    @Test @Order(6) @DisplayName("S9-T06")
    void checkS9T06() {
        JsonNode adminCheckDashboardLockBefore = adminReadDashboardFlowData();
        String username = testUser(adminDashboardFlowSession, "CLIENT")[0];
        Session adminCheckDashboardLockedGuest = browser();
        for (int adminCheckDashboardLoginAttempt = 1; adminCheckDashboardLoginAttempt < 5;
                adminCheckDashboardLoginAttempt++) {
            adminCheckDashboardLockedGuest.login(username, "DashboardWrongPassword42").expect(401)
                    .code("SHARED_LOGIN_FAILED");
        }
        adminCheckDashboardLockedGuest.login(username, "DashboardWrongPassword42").expect(401)
                .code("SHARED_ACCOUNT_LOCKED");
        JsonNode adminCheckDashboardLockAfter = adminReadDashboardFlowData();
        adminCheckDashboardCountChange(adminCheckDashboardLockBefore, adminCheckDashboardLockAfter,
                "LockedUserCount", 1);
        adminCheckDashboardListCount(adminCheckDashboardLockAfter, "LockedUserCount",
                USERS + "?adminSearchUserAccountStatus=LOCKED_NOW", "adminSearchUser");
        JsonNode adminCheckDashboardLockedRows = adminDashboardFlowSession
                .get(USERS + "?adminSearchUserAccountStatus=LOCKED_NOW&adminSearchUserKeyword=" + username)
                .expect(200).json().path("adminSearchUserRowList");
        assertEquals(1, adminCheckDashboardLockedRows.size());
        assertEquals(username, adminCheckDashboardLockedRows.get(0).path("username").asText());
        assertTrue(adminCheckDashboardLockedRows.get(0).path("adminSearchUserIsLockedNow").asBoolean());
        reportPartial("Opening a private browser and seeing the Users page choose the Locked now filter.");
    }

    // ===== SECTION 3: PRIVACY, TEST MODE AND DOCUMENT CHECKS =====

    // What it does: Explains where to verify the session countdown without browser requests.
    // Input: None. Output: A skip with the two-minute browser procedure recorded in S9-H06.
    @Test @Order(7) @DisplayName("S9-T07")
    @Disabled("Watch the countdown on an untouched dashboard for two minutes in S9-H06.")
    void checkS9T07() {
    }

    // What it does: Runs the privacy tool with synthetic scan and log data.
    // Input: The existing flow orders and the two Stage 3 fixture tools.
    // Output: No findings, the exact number of answers inspected and no download audit rows.
    @Test @Order(8) @DisplayName("S9-T08")
    void checkS9T08() {
        adminDashboardFlowSession.tool("picking-scan-rows", null).expect(200);
        adminDashboardFlowSession.tool("private-value-log-rows", null).expect(200);
        long adminCheckPrivacyOrderCount = Math.min(20, count("SELECT COUNT(*) FROM customer_order"));
        long adminCheckPrivacyAuditCount = count("SELECT COUNT(*) FROM audit_log WHERE action = 'CSV_DOWNLOADED'");
        assertEquals("0 findings, " + (20 + adminCheckPrivacyOrderCount) + " answers checked.",
                adminDashboardFlowSession.tool("privacy-check", null).expect(200).json().path("message").asText());
        assertEquals(adminCheckPrivacyAuditCount, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CSV_DOWNLOADED'"));
        reportPartial("Pressing the three tool buttons and reading the privacy result in the browser.");
    }

    // What it does: Opens all normal Admin pages and checks refunds with the test profile disabled.
    // Input: A separate HTTP server using the same flow database without admintest.
    // Output: All normal pages answer, provider outcome is UNKNOWN and all Stage 9 test routes give 404.
    @Test @Order(9) @DisplayName("S9-T09")
    void checkS9T09() {
        long refundID = adminCreateDashboardRefund("REQUESTED");
        try (var adminCheckNoTestMode = new SpringApplicationBuilder(BackendApplication.class)
                .run("--server.port=0", "--spring.profiles.active=", "--spring.main.banner-mode=off")) {
            int adminCheckNoTestModePort =
                    ((ServletWebServerApplicationContext) adminCheckNoTestMode).getWebServer().getPort();
            Session adminCheckNoTestModeSession = browser(adminCheckNoTestModePort);
            adminCheckNoTestModeSession.login(bootstrapUsername, FLOW_ADMIN_PASSWORD).expect(200);
            assertFalse(adminCheckNoTestModeSession.get(SESSION).expect(200).json()
                    .path("sharedViewSessionTestModeIsEnabled").asBoolean());
            for (String adminCheckNoTestModePage : List.of(ADMIN_DASHBOARD, "/admin/admin-users.html",
                    "/admin/admin-products.html", "/admin/admin-orders.html", "/admin/admin-order-detail.html",
                    "/admin/admin-warehouse-cases.html", "/admin/admin-delivery-cases.html",
                    "/admin/admin-refunds.html", "/admin/admin-logs.html", CHANGE_PASSWORD)) {
                adminCheckNoTestModeSession.get(adminCheckNoTestModePage).expect(200);
            }
            adminCheckNoTestModeSession.get(ADMIN_DASHBOARD_DATA).expect(200);
            adminCheckNoTestModeSession.get(ADMIN_MENU_COUNTERS).expect(200);
            JsonNode adminCheckNoProvider = adminCheckNoTestModeSession.post("/api/admin/refunds/" + refundID + "/check",
                    null).expect(200).json();
            assertEquals("UNKNOWN", adminCheckNoProvider.path("adminCheckRefundStatusProviderOutcome").asText());
            assertTrue(adminCheckNoProvider.path("adminCheckRefundStatusMessage").asText().toLowerCase(Locale.ROOT)
                    .contains("no payment provider is connected yet"));
            assertEquals("REQUESTED", database.sql("SELECT refund_status FROM refund WHERE refund_id = :refundID")
                    .param("refundID", refundID).query(String.class).single());
            adminCheckNoTestModeSession.get("/api/admin/temporary-test/mode").expect(404);
            adminCheckNoTestModeSession.tool("privacy-check", null).expect(404);
            adminCheckNoTestModeSession.tool("remove-all-test-data",
                    Map.of("adminTemporaryRemoveAllTestDataConfirmText", "REMOVE")).expect(404);
        }
        adminDashboardFlowSession.get("/api/admin/temporary-test/mode").expect(200);
        reportPartial("Changing and restoring Compose and checking the visible frame on every page (S9-H08).");
    }

    // What it does: Checks the documented integration IDs against the marked backend code.
    // Input: The placeholder list and the Java and JavaScript sources.
    // Output: All 18 integration IDs and every marked source file appear in the list.
    @Test @Order(10) @DisplayName("S9-T10")
    void checkS9T10() throws IOException {
        String adminCheckPlaceholderList = Files.readString(Path.of("/flow-docs/PLACEHOLDER_LIST.md"));
        for (int adminCheckPlaceholderIndex = 1; adminCheckPlaceholderIndex <= 10; adminCheckPlaceholderIndex++) {
            assertTrue(adminCheckPlaceholderList.contains("PO-%02d".formatted(adminCheckPlaceholderIndex)));
            if (adminCheckPlaceholderIndex < 9) {
                assertTrue(adminCheckPlaceholderList.contains("PI-%02d".formatted(adminCheckPlaceholderIndex)));
            }
        }
        adminCheckStageNineMarkedFiles(adminCheckPlaceholderList, Pattern.compile("PLACEHOLDER" + " (INPUT|OUTPUT)"));
        reportPartial("Reading each integration entry and matching its owning function by hand (S9-H09).");
    }

    // What it does: Checks that marked temporary files and build wiring appear in the removal list.
    // Input: The temporary code list, the source tree, the backend Dockerfile and Compose file.
    // Output: Every marked path has an entry in the removal list.
    @Test @Order(11) @DisplayName("S9-T11")
    void checkS9T11() throws IOException {
        String adminCheckTemporaryList = Files.readString(Path.of("/flow-docs/TEMPORARY_CODE_LIST.md"));
        adminCheckStageNineMarkedFiles(adminCheckTemporaryList, Pattern.compile("TEMPORARY" + " TEST CODE"));
        assertTrue(adminCheckTemporaryList.contains("Dockerfile"));
        assertTrue(adminCheckTemporaryList.contains("compose.yaml"));
        assertTrue(Files.isRegularFile(Path.of("/flow-docs/Dockerfile")));
        assertTrue(Files.isRegularFile(Path.of("/flow-docs/compose.yaml")));
        reportPartial("Reviewing individual marker descriptions and verifying the listed functions by hand (S9-H09).");
    }

    // What it does: Verifies the required comment format in five representative files.
    // Input: Three Java and two JavaScript files. Output: Numbered sections and function documentation.
    @Test @Order(12) @DisplayName("S9-T12")
    void checkS9T12() throws IOException {
        String adminCheckSourceRoot = "src/main/java/com/lms/backend/admin/";
        String adminCheckScriptRoot = "src/main/resources/static/admin/";
        for (String adminCheckSourcePath : List.of(adminCheckSourceRoot + "dashboard/AdminDashboardController.java",
                adminCheckSourceRoot + "dashboard/AdminDashboardService.java",
                adminCheckSourceRoot + "dashboard/AdminDashboardRule.java",
                adminCheckScriptRoot + "admin-dashboard.js", adminCheckScriptRoot + "admin-temporary-test-tools.js")) {
            String adminCheckSourceText = Files.readString(Path.of(adminCheckSourcePath));
            assertTrue(adminCheckSourceText.contains("SECTION 1:"), adminCheckSourcePath);
            assertTrue(adminCheckSourceText.contains("What it does:"), adminCheckSourcePath);
            assertTrue(adminCheckSourceText.contains("Input:"), adminCheckSourcePath);
            assertTrue(adminCheckSourceText.contains("Output:"), adminCheckSourcePath);
        }
        reportPartial("Inspecting every function comment and the naming rule in the five chosen files (S9-H10).");
    }

    // What it does: Refuses lowercase confirmation, then removes only the specified test data.
    // Input: Existing flow fixtures, a new test worker and the two confirmation attempts.
    // Output: The data survives the refusal, then all 25 ordinary tables are empty and Admin still works.
    @Test @Order(14) @DisplayName("S9-T14")
    void checkS9T14() {
        String username = testUser(adminDashboardFlowSession, "DELIVERY_WORKER")[0];
        long userID = userID(username);
        database.sql("INSERT INTO delivery_worker_profile (user_id, employee_number) VALUES (:userID, :username)")
                .param("userID", userID).param("username", username).update();
        long adminCheckCleanupOrderCount = count("SELECT COUNT(*) FROM customer_order");
        assertTrue(adminCheckCleanupOrderCount > 0);
        adminDashboardFlowSession.tool("remove-all-test-data",
                Map.of("adminTemporaryRemoveAllTestDataConfirmText", "remove")).expect(400).code("SHARED_INPUT_INVALID");
        assertEquals(adminCheckCleanupOrderCount, count("SELECT COUNT(*) FROM customer_order"));
        assertEquals(userID, userID(username));

        adminDashboardFlowSession.tool("remove-all-test-data",
                Map.of("adminTemporaryRemoveAllTestDataConfirmText", "REMOVE")).expect(200);
        var adminCheckCleanupTableList = database.sql("""
                SELECT tablename FROM pg_tables WHERE schemaname = 'public'
                AND tablename NOT IN ('app_user', 'delivery_worker_profile')
                """).query(String.class).list();
        assertEquals(25, adminCheckCleanupTableList.size());
        for (String adminCheckCleanupTable : adminCheckCleanupTableList) {
            assertEquals(0, count("SELECT COUNT(*) FROM " + adminCheckCleanupTable), adminCheckCleanupTable);
        }
        assertEquals(0, count("SELECT COUNT(*) FROM app_user WHERE left(username, 5) = 'test_'"));
        assertEquals(0, count("SELECT COUNT(*) FROM delivery_worker_profile WHERE user_id = " + userID));
        adminDashboardFlowSession.get(USERS).expect(200);
        adminDashboardFlowSession.get("/api/admin/products").expect(200);
        adminDashboardFlowSession.get("/api/admin/orders").expect(200);
        reportPartial("Entering both confirmation values and reading the Users, Products and Orders lists (S9-H13).");
    }

    // ===== SECTION 4: MENU COUNTER FLOWS =====

    // What it does: Compares the menu to the dashboard on several page loads without writing an audit row.
    // Input: Dashboard, Users and Logs pages, first after cleanup and then with all three work counts present.
    // Output: The same counters with and without fixtures and no audit entry for reading them.
    @Test @Order(16) @DisplayName("S9-T16")
    void checkS9T16() {
        JsonNode adminCheckEmptyMenu = adminReadDashboardMenu();
        for (String adminCheckMenuField : List.of("OpenShortageCaseCount", "PendingClientInstructionCount",
                "UnresolvedRefundCount")) {
            assertEquals(0, adminCheckEmptyMenu.path(ADMIN_MENU_COUNTER_PREFIX + adminCheckMenuField).asLong());
        }
        adminCreateDashboardShortage();
        adminDashboardFlowSession.tool("failed-delivery-case", Map.of(
                "adminTemporaryCreateFailedDeliveryCaseFailureCount", 2,
                "adminTemporaryCreateFailedDeliveryCaseIsReturned", true,
                "adminTemporaryCreateFailedDeliveryCaseDaysSinceReturn", 8,
                "adminTemporaryCreateFailedDeliveryCaseClientRequest", "REDELIVER")).expect(200);
        adminCreateDashboardRefund("REQUESTED");

        long adminCheckMenuAuditCount = count("SELECT COUNT(*) FROM audit_log");
        JsonNode adminCheckMenuDashboard = adminReadDashboardFlowData();
        for (String adminCheckMenuPage : List.of(ADMIN_DASHBOARD, "/admin/admin-users.html",
                "/admin/admin-logs.html")) {
            adminDashboardFlowSession.get(adminCheckMenuPage).expect(200);
            JsonNode adminCheckMenu = adminReadDashboardMenu();
            for (String adminCheckMenuField : List.of("OpenShortageCaseCount", "PendingClientInstructionCount",
                    "UnresolvedRefundCount")) {
                assertEquals(adminCheckMenuDashboard.path(ADMIN_DASHBOARD_PREFIX + adminCheckMenuField),
                        adminCheckMenu.path(ADMIN_MENU_COUNTER_PREFIX + adminCheckMenuField));
                assertEquals(1, adminCheckMenu.path(ADMIN_MENU_COUNTER_PREFIX + adminCheckMenuField).asLong());
            }
        }
        assertEquals(adminCheckMenuAuditCount, count("SELECT COUNT(*) FROM audit_log"));
        reportPartial("Rendered side menu numbers and hiding the badge when its count is zero.");
    }

    // What it does: Reads the changed work counter in the same session after a shortage decision.
    // Input: A new shortage and the Wait for restock action.
    // Output: The shortage counter rises for a new report and returns to its earlier value after the decision.
    @Test @Order(17) @DisplayName("S9-T17")
    void checkS9T17() {
        String adminCheckMenuShortageField = "adminViewMenuCounterOpenShortageCaseCount";
        long adminCheckMenuShortageBefore = adminReadDashboardMenu().path(adminCheckMenuShortageField).asLong();
        long shortageCaseID = adminCreateDashboardShortage();
        assertEquals(adminCheckMenuShortageBefore + 1,
                adminReadDashboardMenu().path(adminCheckMenuShortageField).asLong());
        adminWaitDashboardShortage(shortageCaseID);
        assertEquals(adminCheckMenuShortageBefore, adminReadDashboardMenu().path(adminCheckMenuShortageField).asLong());
        reportPartial("Automatic badge refresh immediately after the successful action on the Warehouse cases page.");
    }

    // What it does: Proves that a Warehouse Worker cannot read the Admin dashboard or counters.
    // Input: The mei_picker account from the earlier flows.
    // Output: The Admin routes return 403 SHARED_ACCESS_DENIED.
    @Test @Order(18) @DisplayName("S9-T18")
    void checkS9T18() {
        Session adminCheckDashboardWorker = browser();
        adminCheckDashboardWorker.login("mei_picker", FLOW_PICKER_PASSWORD).expect(200);
        adminCheckDashboardWorker.get(ADMIN_MENU_COUNTERS).expect(403).code("SHARED_ACCESS_DENIED");
        adminCheckDashboardWorker.get(ADMIN_DASHBOARD_DATA).expect(403).code("SHARED_ACCESS_DENIED");
    }

    // What it does: Verifies the bootstrap super admin after the full flow run on the newly created database.
    // Input: The same flow database initialized by Stage 0 and signed in to in Stage 1.
    // Output: The 27 tables and a functioning super-admin account.
    @Test @Order(19) @DisplayName("S9-T15")
    void checkS9T15() {
        assertEquals(27, count("SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public'"));
        JsonNode adminCheckFinalAdmin = admin().get(USERS + "?adminSearchUserKeyword=" + bootstrapUsername)
                .expect(200).json().path("adminSearchUserRowList");
        assertTrue(StreamSupport.stream(adminCheckFinalAdmin.spliterator(), false)
                .anyMatch(adminCheckFinalAdminRow -> adminCheckFinalAdminRow.path("username").asText()
                        .equals(bootstrapUsername) && adminCheckFinalAdminRow.path("adminSearchUserIsSuperAdmin").asBoolean()
                        && !adminCheckFinalAdminRow.path("mustChangePassword").asBoolean()));
        reportPartial("Deleting the local Compose volume and completing bootstrap login in a real browser (S9-H01).");
    }

    // ===== SECTION 5: HELPER FUNCTIONS =====

    // What it does: Finds source files with the selected marker and verifies that each has a documented location.
    // Input: The relevant inventory and marker pattern. Output: Assertions about every marked source file.
    private void adminCheckStageNineMarkedFiles(String adminCheckStageNineInventory,
            Pattern adminCheckStageNineMarker) throws IOException {
        try (var adminCheckStageNineSourceStream = Files.walk(Path.of("src"))) {
            for (Path adminCheckStageNinePath : adminCheckStageNineSourceStream.filter(Files::isRegularFile)
                    .filter(adminCheckStageNineSource -> adminCheckStageNineSource.toString()
                            .matches(".*\\.(java|html|js|properties)$")).toList()) {
                if (adminCheckStageNineMarker.matcher(Files.readString(adminCheckStageNinePath)).find()) {
                    assertTrue(adminCheckStageNineInventory.contains(adminCheckStageNinePath.getFileName().toString()),
                            adminCheckStageNinePath.toString());
                }
            }
        }
    }

    // What it does: Reads a fresh dashboard from the signed-in flow session.
    // Input: None. Output: The live dashboard JSON.
    private JsonNode adminReadDashboardFlowData() {
        return adminDashboardFlowSession.get(ADMIN_DASHBOARD_DATA).expect(200).json();
    }

    // What it does: Reads fresh menu counters from the signed-in flow session.
    // Input: None. Output: The live counter JSON.
    private JsonNode adminReadDashboardMenu() {
        return adminDashboardFlowSession.get(ADMIN_MENU_COUNTERS).expect(200).json();
    }

    // What it does: Compares a filtered list total with its corresponding dashboard number.
    // Input: Dashboard JSON, count field suffix, list address and list response prefix. Output: Equality assertion.
    private void adminCheckDashboardListCount(
            JsonNode adminCheckDashboardListDashboard, String adminCheckDashboardField,
            String adminCheckDashboardListWebAddress, String adminCheckDashboardListPrefix) {
        JsonNode adminCheckDashboardList = adminDashboardFlowSession.get(adminCheckDashboardListWebAddress)
                .expect(200).json();
        assertEquals(adminCheckDashboardList.path(adminCheckDashboardListPrefix + "TotalRowCount"),
                adminCheckDashboardListDashboard.path(ADMIN_DASHBOARD_PREFIX + adminCheckDashboardField),
                adminCheckDashboardField);
    }

    // What it does: Compares the dashboard count before and after a fixture or decision.
    // Input: Two dashboard snapshots, count suffix and expected difference. Output: Count change assertion.
    private void adminCheckDashboardCountChange(JsonNode adminCheckDashboardCountBefore,
            JsonNode adminCheckDashboardCountAfter, String adminCheckDashboardCountField,
            long adminCheckDashboardCountDifference) {
        String adminCheckDashboardCountName = ADMIN_DASHBOARD_PREFIX + adminCheckDashboardCountField;
        assertEquals(adminCheckDashboardCountBefore.path(adminCheckDashboardCountName).asLong()
                        + adminCheckDashboardCountDifference,
                adminCheckDashboardCountAfter.path(adminCheckDashboardCountName).asLong(),
                adminCheckDashboardCountName);
    }

    // What it does: Finds the products currently displayed in the low-stock box.
    // Input: The dashboard response. Output: Its displayed SKUs.
    private List<String> adminReadDashboardStockSKUList(JsonNode adminReadDashboardStockResponse) {
        JsonNode adminReadDashboardStockList =
                adminReadDashboardStockResponse.path("adminViewDashboardLowStockProductList");
        assertTrue(adminReadDashboardStockList.isArray());
        assertTrue(adminReadDashboardStockList.size() <= 5);
        return StreamSupport.stream(adminReadDashboardStockList.spliterator(), false)
                .map(adminReadDashboardStockProduct -> adminReadDashboardStockProduct.path("SKU").asText()).toList();
    }

    // What it does: Reports a new shortage against a Picking order.
    // Input: None. Output: The saved case ID from the tool response.
    private long adminCreateDashboardShortage() {
        String adminCreateDashboardShortageMessage = adminDashboardFlowSession.tool("shortage-report", Map.of(
                "adminTemporaryCreateShortageReportOrderStatus", "PICKING",
                "adminTemporaryCreateShortageReportMissingQuantity", 1)).expect(200).json().path("message").asText();
        var adminCreateDashboardShortageMatch =
                ADMIN_DASHBOARD_SHORTAGE_MESSAGE.matcher(adminCreateDashboardShortageMessage);
        assertTrue(adminCreateDashboardShortageMatch.matches(), adminCreateDashboardShortageMessage);
        return Long.parseLong(adminCreateDashboardShortageMatch.group(1));
    }

    // What it does: Decides to wait for the Warehouse Worker to restock.
    // Input: The created case ID. Output: A successful live decision.
    private void adminWaitDashboardShortage(long shortageCaseID) {
        adminDashboardFlowSession.post("/api/admin/shortage-cases/" + shortageCaseID + "/wait",
                Map.of("adminDecideShortageWaitReason", "Wait for the missing units to be replaced")).expect(200);
    }

    // What it does: Creates one refund from Test tools.
    // Input: The initial refund status. Output: The ID of the created refund.
    private long adminCreateDashboardRefund(String refundStatus) {
        String adminCreateDashboardRefundMessage = adminDashboardFlowSession.tool("refund-case", Map.of(
                "adminTemporaryCreateRefundCaseRefundStatus", refundStatus,
                "adminTemporaryCreateRefundCaseAgeHours", 30)).expect(200).json().path("message").asText();
        var adminCreateDashboardRefundMatch = ADMIN_DASHBOARD_REFUND_MESSAGE.matcher(adminCreateDashboardRefundMessage);
        assertTrue(adminCreateDashboardRefundMatch.matches(), adminCreateDashboardRefundMessage);
        return Long.parseLong(adminCreateDashboardRefundMatch.group(1));
    }
}
