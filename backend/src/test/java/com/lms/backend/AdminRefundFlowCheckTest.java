// TEMPORARY TEST CODE - remove before integration
// Stage 8 checks refund actions and the v2.7 page markup through the live server.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AdminRefundFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: ROUTES AND FIXTURES =====

    private static final String ADMIN_REFUNDS = "/api/admin/refunds";
    private static final String ADMIN_REFUND_PAGE = "/admin/admin-refunds.html";
    private static final String ADMIN_REFUND_FLOW_REASON = "The provider confirmed the first attempt failed";
    private static final Pattern ADMIN_REFUND_FIXTURE_MESSAGE =
            Pattern.compile("^Created order (TEST-\\d+), refund (\\d+), payment (\\d+)\\.$");
    private static final Pattern ADMIN_SHORTAGE_FIXTURE_MESSAGE =
            Pattern.compile("^Created shortage case (\\d+) for order (TEST-\\d+)\\.$");
    private static final Pattern ADMIN_FIXTURE_REFERENCE = Pattern.compile("TEST-\\d+");
    private static final JsonMapper ADMIN_AUDIT_JSON = JsonMapper.builder().build();

    private Session adminCheckRefundSession;
    private RefundFixture adminCheckRefundRequested;
    private RefundFixture adminCheckRefundPending;
    private RefundFixture adminCheckRefundFailed;
    private RefundFixture adminCheckRefundFinished;
    private long adminCheckRefundReplacementID;

    private record RefundFixture(String orderReference, long refundID, long paymentID) { }

    // ===== SECTION 2: REFUND FLOWS =====

    // What it does: Creates the four refunds and checks filtering, oldest-first order and ages.
    // Input: A signed-in Admin and the refund fixture tool. Output: The same four fixture IDs for later cases.
    @Test
    @DisplayName("S8-T01")
    void checkS8T01() {
        adminCheckRefundSession = admin();
        adminCheckRefundRequested = adminCreateFlowRefund("REQUESTED", 50);
        adminCheckRefundPending = adminCreateFlowRefund("PENDING", 5);
        adminCheckRefundFailed = adminCreateFlowRefund("FAILED", 30);
        adminCheckRefundFinished = adminCreateFlowRefund("REFUNDED", 100);

        List<RefundFixture> adminCheckFilteredFixtures =
                List.of(adminCheckRefundRequested, adminCheckRefundFailed, adminCheckRefundPending);
        assertEquals(adminCheckFilteredFixtures.stream().map(RefundFixture::refundID).toList(),
                adminReadFlowRefunds(true).stream().map(row -> row.path("refundID").asLong())
                        .filter(refundID -> List.of(adminCheckRefundRequested.refundID(),
                                adminCheckRefundFailed.refundID(), adminCheckRefundPending.refundID(),
                                adminCheckRefundFinished.refundID()).contains(refundID)).toList());

        List<RefundFixture> adminCheckAllFixtures =
                List.of(adminCheckRefundFinished, adminCheckRefundRequested, adminCheckRefundFailed,
                        adminCheckRefundPending);
        List<JsonNode> adminCheckAllRows = adminReadFlowRefunds(false);
        assertEquals(adminCheckAllFixtures.stream().map(RefundFixture::refundID).toList(),
                adminCheckAllRows.stream().map(row -> row.path("refundID").asLong())
                        .filter(refundID -> adminCheckAllFixtures.stream()
                                .anyMatch(fixture -> fixture.refundID() == refundID)).toList());
        for (RefundFixture adminCheckRefundFixture : adminCheckAllFixtures) {
            JsonNode adminCheckRefundRow = adminFindFlowRefund(adminCheckAllRows, adminCheckRefundFixture.refundID());
            assertEquals(adminCheckRefundFixture.paymentID(), adminCheckRefundRow.path("paymentID").asLong());
            assertEquals(adminCheckRefundFixture.orderReference(),
                    adminCheckRefundRow.path("adminSearchRefundOrderReference").asText());
            assertEquals("CANCELLED", adminCheckRefundRow.path("adminSearchRefundOrderStatus").asText());
        }
        assertEquals(50, adminFindFlowRefund(adminCheckAllRows, adminCheckRefundRequested.refundID())
                .path("adminSearchRefundAgeHours").asLong());
        assertEquals(30, adminFindFlowRefund(adminCheckAllRows, adminCheckRefundFailed.refundID())
                .path("adminSearchRefundAgeHours").asLong());
        assertEquals(5, adminFindFlowRefund(adminCheckAllRows, adminCheckRefundPending.refundID())
                .path("adminSearchRefundAgeHours").asLong());
        assertTrue(adminCheckRefundSession.get(ADMIN_REFUND_PAGE).expect(200).body()
                .contains("A refund is shown as Refunded only after the payment provider confirmed it."));
        reportPartial("Rendered ages, row labels and buttons, and retaining the filter across browser reload.");
    }

    // What it does: Attempts to mark a refund complete directly.
    // Input: The requested fixture. Output: A method error and the unchanged stored status.
    @Test
    @DisplayName("S8-T02")
    void checkS8T02() {
        Reply adminCheckManualUpdate = adminCheckRefundSession.put(
                ADMIN_REFUNDS + "/" + adminCheckRefundRequested.refundID(), Map.of("refundStatus", "REFUNDED"));
        if (adminCheckManualUpdate.response().statusCode() == 404) {
            adminCheckManualUpdate.code("SHARED_NOT_FOUND");
        } else {
            adminCheckManualUpdate.expect(405).code("SHARED_METHOD_NOT_ALLOWED");
        }
        assertEquals(List.of("REQUESTED"), adminReadFlowPaymentRefunds(adminCheckRefundRequested.paymentID()));
        reportPartial("Absence of a manual completion button in the rendered row.");
    }

    // What it does: Checks a requested refund before any test provider answer is set.
    // Input: The requested fixture. Output: UNKNOWN, the saved check time and an unchanged refund.
    @Test
    @DisplayName("S8-T03")
    void checkS8T03() {
        JsonNode adminCheckRefundAnswer = adminSendFlowCheck(adminCheckRefundRequested.refundID()).expect(200).json();
        adminCheckFlowOutcome(adminCheckRefundAnswer, adminCheckRefundRequested, "REQUESTED", "UNKNOWN");
        assertTrue(adminCheckRefundAnswer.path("adminCheckRefundStatusMessage").asText()
                .contains("No payment provider is connected yet."));
        JsonNode adminCheckRefundRow = adminFindFlowRefund(adminReadFlowRefunds(true),
                adminCheckRefundRequested.refundID());
        Instant.parse(adminCheckRefundRow.path("adminSearchRefundLastCheckTime").asText());
        assertEquals("UNKNOWN", adminCheckRefundRow.path("adminSearchRefundLastCheckOutcome").asText());
        reportPartial("Where the message and last provider check appear in the browser.");
    }

    // What it does: Applies a pending provider answer.
    // Input: The requested fixture. Output: The PENDING status in the answer and the saved row.
    @Test
    @DisplayName("S8-T04")
    void checkS8T04() {
        adminSetFlowProviderAnswer(adminCheckRefundRequested.refundID(), "PENDING");
        adminCheckFlowOutcome(adminSendFlowCheck(adminCheckRefundRequested.refundID()).expect(200).json(),
                adminCheckRefundRequested, "PENDING", "PENDING");
        assertEquals(List.of("PENDING"), adminReadFlowPaymentRefunds(adminCheckRefundRequested.paymentID()));
    }

    // What it does: Completes an attempt and checks that a second provider call cannot change it.
    // Input: The same fixture with REFUNDED, followed by a stored FAILED answer.
    // Output: The completed refund, unchanged audit count and a cancelled order.
    @Test
    @DisplayName("S8-T05")
    void checkS8T05() {
        long refundID = adminCheckRefundRequested.refundID();
        adminSetFlowProviderAnswer(refundID, "REFUNDED");
        adminCheckFlowOutcome(adminSendFlowCheck(refundID).expect(200).json(), adminCheckRefundRequested,
                "REFUNDED", "REFUNDED");
        assertFalse(adminReadFlowRefunds(true).stream().anyMatch(row -> row.path("refundID").asLong() == refundID));
        JsonNode adminCheckFinishedRow = adminFindFlowRefund(adminReadFlowRefunds(false), refundID);
        Instant.parse(adminCheckFinishedRow.path("completedTime").asText());
        JsonNode adminCheckLastTime = adminCheckFinishedRow.path("adminSearchRefundLastCheckTime");
        Instant.parse(adminCheckLastTime.asText());
        long adminCheckAuditBefore = adminCountFlowRefundAudit(refundID);

        adminSetFlowProviderAnswer(refundID, "FAILED");
        JsonNode adminCheckAgain = adminSendFlowCheck(refundID).expect(200).json();
        adminCheckFlowOutcome(adminCheckAgain, adminCheckRefundRequested, "REFUNDED", "REFUNDED");
        assertEquals("This refund is already completed.", adminCheckAgain.path("adminCheckRefundStatusMessage").asText());
        assertEquals(adminCheckAuditBefore, adminCountFlowRefundAudit(refundID));
        assertEquals(adminCheckLastTime, adminFindFlowRefund(adminReadFlowRefunds(false), refundID)
                .path("adminSearchRefundLastCheckTime"));
        JsonNode adminCheckOrder = adminReadFlowOrder(adminCheckRefundRequested.orderReference());
        assertEquals("CANCELLED", adminCheckOrder.path("orderStatus").asText());
        assertEquals("REFUNDED", adminCheckOrder.path("adminViewOrderRefundList").get(0).path("refundStatus").asText());
        reportPartial("Rendering of the finished row, its buttons and the order page.");
    }

    // What it does: Applies a failed provider answer to the pending fixture.
    // Input: The five-hour refund. Output: A saved FAILED status.
    @Test
    @DisplayName("S8-T06")
    void checkS8T06() {
        adminSetFlowProviderAnswer(adminCheckRefundPending.refundID(), "FAILED");
        adminCheckFlowOutcome(adminSendFlowCheck(adminCheckRefundPending.refundID()).expect(200).json(),
                adminCheckRefundPending, "FAILED", "FAILED");
        assertEquals(List.of("FAILED"), adminReadFlowPaymentRefunds(adminCheckRefundPending.paymentID()));
        reportPartial("Appearance of the Retry refund button.");
    }

    // What it does: Tries to retry an attempt that the provider reports as already refunded.
    // Input: The failed thirty-hour fixture, with the answer REFUNDED.
    // Output: A refusal with the completed check and audit still saved, and no duplicate payment refund.
    @Test
    @DisplayName("S8-T07")
    void checkS8T07() {
        long refundID = adminCheckRefundFailed.refundID();
        long adminCheckAuditBefore = adminCountFlowRefundAudit(refundID);
        adminSetFlowProviderAnswer(refundID, "REFUNDED");
        Reply adminCheckRetryReply = adminRetryFlowRefund(adminCheckRefundSession, refundID).expect(409)
                .code("ADMIN_REFUND_STATE_CONFLICT");
        assertEquals("The provider reports REFUNDED. A new try is only allowed after a confirmed failure.",
                adminCheckRetryReply.json().path("message").asText());
        assertEquals(List.of("REFUNDED"), adminReadFlowPaymentRefunds(adminCheckRefundFailed.paymentID()));
        assertEquals(adminCheckAuditBefore + 1, adminCountFlowRefundAudit(refundID));
    }

    // What it does: Retries a confirmed failure and reads the replacement, old attempt, and stock.
    // Input: The five-hour failed refund. Output: One new request and no stock or order change.
    @Test
    @DisplayName("S8-T08")
    void checkS8T08() {
        List<Map<String, Object>> adminCheckStockBefore = adminReadFlowRefundStock();
        long refundID = adminCheckRefundPending.refundID();
        JsonNode adminCheckRetryAnswer = adminRetryFlowRefund(adminCheckRefundSession, refundID).expect(200).json();
        adminCheckRefundReplacementID = adminCheckRetryAnswer.path("refundID").asLong();
        assertTrue(adminCheckRefundReplacementID > refundID);
        assertTrue(adminCheckRetryAnswer.path("adminDecidedRowRefundIsNew").asBoolean());
        assertEquals("REQUESTED", adminCheckRetryAnswer.path("adminDecidedRowStateWord").asText());
        assertEquals(bootstrapUsername, adminCheckRetryAnswer.path("adminDecidedRowDecidedByUsername").asText());
        assertFalse(adminCheckRetryAnswer.path("message").asText().isBlank());

        List<JsonNode> adminCheckUnresolvedRows = adminReadFlowRefunds(true);
        assertFalse(adminCheckUnresolvedRows.stream().anyMatch(row -> row.path("refundID").asLong() == refundID));
        JsonNode adminCheckNewRefund = adminFindFlowRefund(adminCheckUnresolvedRows, adminCheckRefundReplacementID);
        assertEquals(adminCheckRetryAnswer.path("adminDecidedRowDecidedTime"), adminCheckNewRefund.path("createdTime"));
        JsonNode adminCheckOldRefund = adminFindFlowRefund(adminReadFlowRefunds(false), refundID);
        assertEquals(adminCheckRefundReplacementID, adminCheckOldRefund.path("adminSearchRefundReplacedByRefundID")
                .asLong());
        assertFalse(adminCheckOldRefund.path("adminSearchRefundIsUnresolved").asBoolean());
        assertEquals(adminCheckOldRefund.path("amount"), adminCheckNewRefund.path("amount"));
        assertEquals(List.of("FAILED", "REQUESTED"), adminReadFlowPaymentRefunds(adminCheckRefundPending.paymentID()));
        assertEquals(adminCheckStockBefore, adminReadFlowRefundStock());
        assertEquals("CANCELLED", adminReadFlowOrder(adminCheckRefundPending.orderReference()).path("orderStatus")
                .asText());
        reportPartial("Retained row, focus, success message and replaced label as rendered before and after Search.");
    }

    // What it does: Blocks older or overlapping retries of a payment.
    // Input: The replaced attempt and a separate failed fixture for two concurrent requests.
    // Output: Unknown and active replacement statuses block, and concurrent retries create exactly one new row.
    @Test
    @DisplayName("S8-T09")
    void checkS8T09() throws Exception {
        long refundID = adminCheckRefundPending.refundID();
        adminRetryFlowRefund(adminCheckRefundSession, refundID).expect(409).code("ADMIN_REFUND_STATE_CONFLICT");
        adminSetFlowStoredRefundStatus(adminCheckRefundReplacementID, "SOMETHING_NEW");
        try {
            JsonNode adminCheckUnknownRefund = adminFindFlowRefund(adminReadFlowRefunds(true),
                    adminCheckRefundReplacementID);
            assertEquals("SOMETHING_NEW", adminCheckUnknownRefund.path("refundStatus").asText());
            adminRetryFlowRefund(adminCheckRefundSession, refundID).expect(409).code("ADMIN_REFUND_STATE_CONFLICT");
        } finally {
            adminSetFlowStoredRefundStatus(adminCheckRefundReplacementID, "REQUESTED");
        }

        RefundFixture adminCheckConcurrentRefund = adminCreateFlowRefund("FAILED", 0);
        adminSetFlowProviderAnswer(adminCheckConcurrentRefund.refundID(), "FAILED");
        Session adminCheckFirstSession = admin();
        Session adminCheckSecondSession = admin();
        CyclicBarrier adminCheckStart = new CyclicBarrier(2);
        try (var adminCheckThreads = Executors.newFixedThreadPool(2)) {
            var adminCheckFirstTry = adminCheckThreads.submit(() -> {
                adminCheckStart.await(10, TimeUnit.SECONDS);
                return adminRetryFlowRefund(adminCheckFirstSession, adminCheckConcurrentRefund.refundID());
            });
            var adminCheckSecondTry = adminCheckThreads.submit(() -> {
                adminCheckStart.await(10, TimeUnit.SECONDS);
                return adminRetryFlowRefund(adminCheckSecondSession, adminCheckConcurrentRefund.refundID());
            });
            Reply adminCheckFirstReply = adminCheckFirstTry.get(30, TimeUnit.SECONDS);
            Reply adminCheckSecondReply = adminCheckSecondTry.get(30, TimeUnit.SECONDS);
            if (adminCheckFirstReply.response().statusCode() == 200) {
                adminCheckSecondReply.expect(409).code("ADMIN_REFUND_STATE_CONFLICT");
            } else {
                adminCheckFirstReply.expect(409).code("ADMIN_REFUND_STATE_CONFLICT");
                adminCheckSecondReply.expect(200);
            }
        }
        assertEquals(List.of("FAILED", "REQUESTED"), adminReadFlowPaymentRefunds(adminCheckConcurrentRefund.paymentID()));
    }

    // What it does: Checks the replacement when the provider cannot report a result.
    // Input: The new REQUESTED refund. Output: An UNKNOWN result with its REQUESTED status preserved.
    @Test
    @DisplayName("S8-T10")
    void checkS8T10() {
        adminSetFlowProviderAnswer(adminCheckRefundReplacementID, "UNKNOWN");
        JsonNode adminCheckAnswer = adminSendFlowCheck(adminCheckRefundReplacementID).expect(200).json();
        assertEquals("REQUESTED", adminCheckAnswer.path("refundStatus").asText());
        assertEquals("UNKNOWN", adminCheckAnswer.path("adminCheckRefundStatusProviderOutcome").asText());
        assertEquals("REQUESTED", adminFindFlowRefund(adminReadFlowRefunds(true), adminCheckRefundReplacementID)
                .path("refundStatus").asText());
        reportPartial("The rendered message and absence of a Retry refund button.");
    }

    // What it does: Cancels a paid shortage and finds the resulting refund in the Admin queue.
    // Input: A synthetic picking shortage. Output: A full-amount REQUESTED refund for the cancelled order.
    @Test
    @DisplayName("S8-T11")
    void checkS8T11() {
        var adminCheckShortage = ADMIN_SHORTAGE_FIXTURE_MESSAGE.matcher(adminCreateFlowShortage("PICKING"));
        assertTrue(adminCheckShortage.matches());
        JsonNode adminCheckCancel = adminCheckRefundSession
                .post("/api/admin/shortage-cases/" + adminCheckShortage.group(1) + "/cancel", Map.of(
                        "adminDecideShortageCancelReason", "Warehouse recorded the damaged stock",
                        "adminDecideShortageCancelStockCorrectionIsConfirmed", true)).expect(200).json();
        JsonNode adminCheckRefund = adminFindFlowRefund(adminReadFlowRefunds(true),
                adminCheckCancel.path("refundID").asLong());
        assertEquals("REQUESTED", adminCheckRefund.path("refundStatus").asText());
        assertEquals(adminCheckShortage.group(2), adminCheckRefund.path("adminSearchRefundOrderReference").asText());
        JsonNode adminCheckOrder = adminReadFlowOrder(adminCheckShortage.group(2));
        assertEquals(0, adminCheckRefund.path("amount").decimalValue()
                .compareTo(adminCheckOrder.path("adminViewOrderPaymentList").get(0).path("amount").decimalValue()));
        reportPartial("Opening the cancellation dialog and reading the resulting row in Refunds.");
    }

    // What it does: Reads the check and retry details from the Admin Logs API.
    // Input: The actions performed above. Output: Persisted old and new values, retry reason and replacement ID.
    @Test
    @DisplayName("S8-T12")
    void checkS8T12() {
        long refundID = adminCheckRefundPending.refundID();
        List<JsonNode> adminCheckLogRows = adminReadFlowAudit("REFUND_STATUS_CHECKED").stream()
                .filter(row -> String.valueOf(refundID).equals(row.path("targetID").asText())).toList();
        assertFalse(adminCheckLogRows.isEmpty());
        assertTrue(adminCheckLogRows.stream().anyMatch(row ->
                "PENDING".equals(ADMIN_AUDIT_JSON.readTree(row.path("oldValue").asText()).path("refundStatus").asText())
                && "FAILED".equals(ADMIN_AUDIT_JSON.readTree(row.path("newValue").asText())
                        .path("providerOutcome").asText())));
        JsonNode adminCheckLatest = ADMIN_AUDIT_JSON.readTree(adminCheckLogRows.getFirst().path("newValue").asText());
        assertEquals("FAILED", adminCheckLatest.path("refundStatus").asText());
        assertEquals(adminCheckRefundPending.orderReference(), adminCheckLatest.path("orderReference").asText());
        JsonNode adminCheckRetryRow = adminReadFlowAudit("REFUND_RETRIED").stream()
                .filter(row -> String.valueOf(refundID).equals(row.path("targetID").asText()))
                .findFirst().orElseThrow();
        assertEquals("REFUND", adminCheckRetryRow.path("targetType").asText());
        JsonNode adminCheckRetryValues = ADMIN_AUDIT_JSON.readTree(adminCheckRetryRow.path("newValue").asText());
        assertEquals(adminCheckRefundReplacementID, adminCheckRetryValues.path("newRefundID").asLong());
        assertEquals(ADMIN_REFUND_FLOW_REASON, adminCheckRetryValues.path("reason").asText());
        assertEquals(adminCheckRefundPending.orderReference(), adminCheckRetryValues.path("orderReference").asText());
        reportPartial("Opening and reading the Audit log tab in the browser.");
    }

    // What it does: Downloads both refund lists and denies a Warehouse worker access to Refunds.
    // Input: Admin and mei_picker sessions. Output: A filtered CSV, its audit row and a role refusal.
    @Test
    @DisplayName("S8-T13")
    void checkS8T13() {
        Reply adminCheckOpenDownload = adminCheckRefundSession.get(ADMIN_REFUNDS + "/csv").expect(200);
        assertTrue(adminCheckOpenDownload.header("Content-Type").startsWith("text/csv"));
        assertTrue(adminCheckOpenDownload.header("Content-Disposition").contains("admin-refunds-"));
        assertTrue(adminCheckOpenDownload.body().startsWith("\ufeff"));
        assertFalse(adminCheckOpenDownload.body().contains(adminCheckRefundFinished.orderReference()));
        Reply adminCheckAllDownload = adminCheckRefundSession
                .get(ADMIN_REFUNDS + "/csv?adminSearchRefundIsUnresolvedOnly=false").expect(200);
        assertTrue(adminCheckAllDownload.body().contains(adminCheckRefundFinished.orderReference()));
        assertTrue(adminReadFlowAudit("CSV_DOWNLOADED").stream()
                .anyMatch(row -> "CSV_REFUNDS".equals(row.path("targetID").asText())));
        Session adminCheckWorker = browser();
        adminCheckWorker.login("mei_picker", FLOW_PICKER_PASSWORD).expect(200);
        adminCheckWorker.get(ADMIN_REFUNDS).expect(403).code("SHARED_ACCESS_DENIED");
        reportPartial("Browser download prompt, filename presentation and opening a private window.");
    }

    // ===== SECTION 3: DESIGN V2.7 MARKUP AND FIXTURES =====

    // What it does: Checks the exact header widths and prepares both dynamic table states.
    // Input: A picking shortage, a rejected product import and the authenticated Admin page responses.
    // Output: One approved width per header in every live table for this stage.
    @Test
    @DisplayName("S8-T15")
    void checkS8T15() {
        var adminCheckPickingShortage = ADMIN_SHORTAGE_FIXTURE_MESSAGE.matcher(adminCreateFlowShortage("PICKING"));
        assertTrue(adminCheckPickingShortage.matches());
        JsonNode adminCheckRejectedImport = adminCheckRefundSession
                .upload("/api/admin/products/import", "adminImportProductCSVFile", """
                        SKU,name,currentPrice,lowStockThreshold
                        BAD SKU,Rejected test product,10,1
                        """).expect(200).json();
        assertEquals(1, adminCheckRejectedImport.path("adminImportProductCSVRejectedCount").asInt());

        adminCheckFlowHeaderWidths("users", "username phrase account-status time actions");
        adminCheckFlowHeaderWidths("products", "code fill money count count count count status-short actions",
                "count fill");
        adminCheckFlowHeaderWidths("orders", "order-flag username time money status-long status-short status-short");
        adminCheckFlowHeaderWidths("warehouse-cases", "order fill time status-long actions",
                "order-flag username time time fill button");
        adminCheckFlowHeaderWidths("delivery-cases", "order-flag fill fill actions",
                "order-flag fill username username time button");
        adminCheckFlowHeaderWidths("refunds", "order fill status word time actions");
        adminCheckFlowHeaderWidths("logs", "time username fill phrase-long fill",
                "time username phrase-long status-short code fill", "time order username code code status-short fill");
        adminCheckFlowHeaderWidths("order-detail", "code fill count count money money", "money status-short fill",
                "count count money status fill", "username time time", "username time time status phrase fill fill",
                "word status word time username time fill", "word fill count status-long fill fill");
        reportPartial("1280 and 1024 px geometry, flag reflow and row content fit in Chrome.");
    }

    // What it does: Checks the native help controls and visible input documentation in the HTML.
    // Input: The authenticated Test tools page. Output: The prescribed disclosures and sample field text.
    @Test
    @DisplayName("S8-T16")
    void checkS8T16() {
        String adminCheckToolMarkup = adminCheckRefundSession.get("/admin/admin-temporary-test-tools.html")
                .expect(200).body();
        // Stage 9 adds two cards. This regression covers the tool cards present through Stage 8.
        String adminCheckPriorToolMarkup = adminCheckToolMarkup.substring(0,
                adminCheckToolMarkup.indexOf("id=\"adminTemporaryRunPrivacyCheckPanel\""));
        assertEquals(18, Pattern.compile("<summary\\b[^>]*>\\s*How to use")
                .matcher(adminCheckPriorToolMarkup).results().count());
        for (String adminCheckToolExample : List.of("e.g. test_client_4", "e.g. test_warehouse_3", "e.g. TEST-MUG-0012",
                "e.g. 10", "e.g. 2", "e.g. 3", "e.g. test_delivery_2", "e.g. 30", "e.g. 12")) {
            assertTrue(adminCheckToolMarkup.contains("placeholder=\"" + adminCheckToolExample + "\""),
                    adminCheckToolExample);
        }
        assertTrue(adminCheckToolMarkup.contains("0 to 720, which is 30 days."));
        assertTrue(adminCheckToolMarkup.contains("Available stock is physical minus reserved, but never below 0."));
        assertFalse(adminCheckToolMarkup.substring(adminCheckToolMarkup.indexOf(">Create many test users</h3>"),
                adminCheckToolMarkup.indexOf(">Create worker with active work</h3>")).contains("<summary"));
        reportPartial("Mouse and Tab/Enter interaction, chevron movement and browser select hit area and popup width.");
    }

    // What it does: Checks the data and markup required by the framed Order detail sections.
    // Input: A failed delivery, an unpaid order and an order with a requested refund.
    // Output: The expected detail lists and count and empty-state elements.
    @Test
    @DisplayName("S8-T17")
    void checkS8T17() {
        String adminCheckDeliveryMessage = adminCheckRefundSession.tool("failed-delivery-case", Map.of(
                "adminTemporaryCreateFailedDeliveryCaseFailureCount", 1,
                "adminTemporaryCreateFailedDeliveryCaseIsReturned", true,
                "adminTemporaryCreateFailedDeliveryCaseDaysSinceReturn", 1,
                "adminTemporaryCreateFailedDeliveryCaseClientRequest", "REDELIVER")).expect(200)
                .json().path("message").asText();
        var adminCheckDeliveryReference = ADMIN_FIXTURE_REFERENCE.matcher(adminCheckDeliveryMessage);
        assertTrue(adminCheckDeliveryReference.find());
        JsonNode adminCheckDeliveryOrder = adminReadFlowOrder(adminCheckDeliveryReference.group());
        assertEquals(1, adminCheckDeliveryOrder.path("adminViewOrderDeliveryAttemptList").size());
        assertEquals(1, adminCheckDeliveryOrder.path("adminViewOrderClientInstructionList").size());

        var adminCheckUnpaidReference = ADMIN_FIXTURE_REFERENCE.matcher(adminCreateFlowShortage("AWAITING_PAYMENT"));
        assertTrue(adminCheckUnpaidReference.find());
        JsonNode adminCheckUnpaidOrder = adminReadFlowOrder(adminCheckUnpaidReference.group());
        assertEquals("AWAITING_PAYMENT", adminCheckUnpaidOrder.path("orderStatus").asText());
        for (String adminCheckPart : List.of("PickingAssignment", "DeliveryAttempt", "ClientInstruction")) {
            assertTrue(adminCheckUnpaidOrder.path("adminViewOrder" + adminCheckPart + "List").isArray());
            assertEquals(0, adminCheckUnpaidOrder.path("adminViewOrder" + adminCheckPart + "List").size());
        }

        RefundFixture adminCheckGridRefund = adminCreateFlowRefund("REQUESTED", 5);
        JsonNode adminCheckRefundOrder = adminReadFlowOrder(adminCheckGridRefund.orderReference());
        assertEquals(1, adminCheckRefundOrder.path("adminViewOrderPaymentList").size());
        assertEquals(1, adminCheckRefundOrder.path("adminViewOrderRefundList").size());
        String adminCheckOrderMarkup = adminCheckRefundSession.get("/admin/admin-order-detail.html?orderID="
                + adminCheckRefundOrder.path("orderID").asLong()).expect(200).body();
        for (String adminCheckPart : List.of("PickingAssignment", "DeliveryAttempt", "ClientInstruction")) {
            assertTrue(adminCheckOrderMarkup.contains("id=\"adminViewOrder" + adminCheckPart + "CountText\""));
            assertTrue(adminCheckOrderMarkup.contains("id=\"adminViewOrder" + adminCheckPart
                    + "EmptyMessage\">No rows found."));
        }
        assertEquals(3, Pattern.compile("<h3 class=\"shared-card-part-title\">")
                .matcher(adminCheckOrderMarkup).results().count());
        reportPartial("Rendered part counts and empty lines, scroll and pinning at 1024 px, and payment/refund grid zoom.");
    }

    // ===== SECTION 4: HELPERS =====

    // What it does: Creates and identifies one test refund.
    // Input: Status and age in hours. Output: Order reference and the two numeric IDs.
    private RefundFixture adminCreateFlowRefund(String refundStatus, int adminCreateFlowRefundAgeHours) {
        String adminCreateFlowRefundMessage = adminCheckRefundSession.tool("refund-case", Map.of(
                "adminTemporaryCreateRefundCaseRefundStatus", refundStatus,
                "adminTemporaryCreateRefundCaseAgeHours", adminCreateFlowRefundAgeHours))
                .expect(200).json().path("message").asText();
        var adminCreateFlowRefundMatch = ADMIN_REFUND_FIXTURE_MESSAGE.matcher(adminCreateFlowRefundMessage);
        assertTrue(adminCreateFlowRefundMatch.matches(), "Expected the created order reference, refund and payment.");
        return new RefundFixture(adminCreateFlowRefundMatch.group(1), Long.parseLong(adminCreateFlowRefundMatch.group(2)),
                Long.parseLong(adminCreateFlowRefundMatch.group(3)));
    }

    // What it does: Creates a shortage with the documented sample tool.
    // Input: The order's status. Output: The fixture tool message.
    private String adminCreateFlowShortage(String orderStatus) {
        return adminCheckRefundSession.tool("shortage-report", Map.of(
                "adminTemporaryCreateShortageReportOrderStatus", orderStatus,
                "adminTemporaryCreateShortageReportMissingQuantity", 1)).expect(200).json().path("message").asText();
    }

    // What it does: Sets a provider answer for one test refund.
    // Input: Its numerical ID and the provider outcome. Output: A successful tool response.
    private void adminSetFlowProviderAnswer(long refundID, String adminSetFlowProviderOutcome) {
        adminCheckRefundSession.tool("provider-answer", Map.of(
                "adminTemporarySetProviderAnswerRefundID", refundID,
                "adminTemporarySetProviderAnswerOutcome", adminSetFlowProviderOutcome)).expect(200);
    }

    // What it does: Sends the provider check or a retry to the live API.
    // Input: Refund ID, and the Admin session for a retry. Output: The action reply.
    private Reply adminSendFlowCheck(long refundID) {
        return adminCheckRefundSession.post(ADMIN_REFUNDS + "/" + refundID + "/check", null);
    }

    // What it does: Requests a new attempt using the plan's reason field.
    // Input: An Admin session and existing refund ID. Output: Success or a rule refusal.
    private Reply adminRetryFlowRefund(Session adminRetryFlowSession, long refundID) {
        return adminRetryFlowSession.post(ADMIN_REFUNDS + "/" + refundID + "/retry",
                Map.of("adminRetryRefundReason", ADMIN_REFUND_FLOW_REASON));
    }

    // What it does: Checks that the provider response identifies the refund and the two statuses.
    // Input: Check response, fixture, stored status and provider outcome. Output: Assertions on the exact fields.
    private void adminCheckFlowOutcome(JsonNode adminCheckFlowAnswer, RefundFixture adminCheckFlowFixture,
            String refundStatus, String adminCheckFlowProviderOutcome) {
        assertEquals(adminCheckFlowFixture.refundID(), adminCheckFlowAnswer.path("refundID").asLong());
        assertEquals(refundStatus, adminCheckFlowAnswer.path("refundStatus").asText());
        assertEquals(adminCheckFlowProviderOutcome, adminCheckFlowAnswer.path("adminCheckRefundStatusProviderOutcome")
                .asText());
    }

    // What it does: Reads the case and audit lists through their live pagination.
    // Input: The refund filter or audit action. Output: Matching rows in the server's order.
    private List<JsonNode> adminReadFlowRefunds(boolean adminReadFlowIsUnresolvedOnly) {
        return adminReadFlowPages(ADMIN_REFUNDS + "?adminSearchRefundIsUnresolvedOnly=" + adminReadFlowIsUnresolvedOnly,
                "adminSearchRefund");
    }

    // What it does: Filters the audit log to one action.
    // Input: Audit action. Output: Matching rows in the server's order.
    private List<JsonNode> adminReadFlowAudit(String adminReadFlowAuditAction) {
        return adminReadFlowPages("/api/admin/logs/audit?adminSearchAuditLogAction=" + adminReadFlowAuditAction,
                "adminSearchAuditLog");
    }

    // What it does: Reads a list page by page using its matching paging fields.
    // Input: Filtered URL and the API name prefix. Output: All returned list rows.
    private List<JsonNode> adminReadFlowPages(String adminReadFlowPageWebAddress, String adminReadFlowPrefix) {
        List<JsonNode> adminReadFlowRows = new ArrayList<>();
        int adminReadFlowPage = 1;
        int adminReadFlowTotalPages;
        do {
            JsonNode adminReadFlowAnswer = adminCheckRefundSession
                    .get(adminReadFlowPageWebAddress + "&" + adminReadFlowPrefix + "PageNumber=" + adminReadFlowPage)
                    .expect(200).json();
            adminReadFlowAnswer.path(adminReadFlowPrefix + "RowList").forEach(adminReadFlowRows::add);
            adminReadFlowTotalPages = adminReadFlowAnswer.path(adminReadFlowPrefix + "TotalPageCount").asInt();
            adminReadFlowPage++;
        } while (adminReadFlowPage <= adminReadFlowTotalPages);
        return adminReadFlowRows;
    }

    // What it does: Finds one refund by its ID.
    // Input: Refund rows and expected ID. Output: The exact row.
    private JsonNode adminFindFlowRefund(List<JsonNode> adminFindFlowRefundRows, long refundID) {
        return adminFindFlowRefundRows.stream().filter(row -> row.path("refundID").asLong() == refundID)
                .findFirst().orElseThrow(() -> new AssertionError("Missing refund " + refundID));
    }

    // What it does: Reads an order through its detail API.
    // Input: The sample order reference. Output: Current read-only detail.
    private JsonNode adminReadFlowOrder(String orderReference) {
        long orderID = database.sql("SELECT order_id FROM customer_order WHERE order_reference = :orderReference")
                .param("orderReference", orderReference).query(Long.class).single();
        return adminCheckRefundSession.get("/api/admin/orders/" + orderID).expect(200).json();
    }

    // What it does: Reads the stock and reservation balances for test products.
    // Input: The isolated flow database. Output: Synthetic inventory ordered by SKU.
    private List<Map<String, Object>> adminReadFlowRefundStock() {
        return database.sql("""
                SELECT p.sku, i.physical_stock, i.reserved_stock FROM product p JOIN inventory i USING (product_id)
                WHERE p.sku LIKE 'TEST-%' ORDER BY p.sku
                """).query().listOfRows();
    }

    // What it does: Counts saved provider-check rows for an attempt.
    // Input: The refund ID. Output: The number of checks recorded against that refund.
    private long adminCountFlowRefundAudit(long refundID) {
        return database.sql("""
                SELECT count(*) FROM audit_log
                WHERE action = 'REFUND_STATUS_CHECKED' AND target_type = 'REFUND' AND target_id = :targetID
                """).param("targetID", String.valueOf(refundID)).query(Long.class).single();
    }

    // What it does: Reads the attempts in refund-ID order for a payment.
    // Input: The payment ID. Output: Stored statuses of all attempts.
    private List<String> adminReadFlowPaymentRefunds(long paymentID) {
        return database.sql("SELECT refund_status FROM refund WHERE payment_id = :paymentID ORDER BY refund_id")
                .param("paymentID", paymentID).query(String.class).list();
    }

    // What it does: Stages or restores a draft status directly in the flow-only database.
    // Input: The refund ID and desired word. Output: One changed row.
    private void adminSetFlowStoredRefundStatus(long refundID, String refundStatus) {
        assertEquals(1, database.sql("UPDATE refund SET refund_status = :refundStatus WHERE refund_id = :refundID")
                .param("refundStatus", refundStatus).param("refundID", refundID).update());
    }

    // What it does: Reads each table's header widths in displayed order.
    // Input: A live Admin page and the expected width sequence of each header.
    // Output: Exactly one matching width class per column, with every headed table checked.
    private void adminCheckFlowHeaderWidths(String adminCheckWidthsPage, String... adminCheckWidthsExpected) {
        String adminCheckWidthsMarkup = adminCheckRefundSession.get("/admin/admin-" + adminCheckWidthsPage + ".html")
                .expect(200).body();
        Pattern adminCheckHeaderCell = Pattern.compile("<th\\b[^>]*>");
        Pattern adminCheckColumnWidth = Pattern.compile("\\bshared-column-([\\w-]+)");
        List<String> adminCheckWidthRows = Pattern.compile("(?s)<thead\\b[^>]*>(.*?)</thead>")
                .matcher(adminCheckWidthsMarkup).results().map(header -> {
                    var adminCheckWidthCells = adminCheckHeaderCell.matcher(header.group(1)).results().toList();
                    return String.join(" ", adminCheckWidthCells.stream().map(cell -> {
                        var adminCheckWidthClass = adminCheckColumnWidth.matcher(cell.group()).results().toList();
                        assertEquals(1, adminCheckWidthClass.size(), adminCheckWidthsPage + " " + cell.group());
                        return adminCheckWidthClass.getFirst().group(1);
                    }).toList());
                }).toList();
        assertEquals(List.of(adminCheckWidthsExpected), adminCheckWidthRows, adminCheckWidthsPage);
    }
}
