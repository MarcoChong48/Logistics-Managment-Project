// TEMPORARY TEST CODE - remove before integration
// Stage 7 checks Delivery cases using live Admin sessions and the isolated flow database.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AdminDeliveryCaseFlowCheckTest extends SharedFlowCheck {
    // ===== SECTION 1: ADDRESSES AND FIXTURES =====

    private static final String ADMIN_FAILED_CASES = "/api/admin/failed-delivery-cases";
    private static final String ADMIN_HELD_PACKAGES = "/api/admin/held-packages";
    private static final String ADMIN_FLOW_INSTRUCTIONS = "/api/admin/client-instructions";
    private static final String ADMIN_FAILED_PREFIX = "adminSearchFailedDeliveryCase";
    private static final String ADMIN_HELD_PREFIX = "adminSearchHeldPackage";
    private static final String ADMIN_FLOW_IDENTITY_METHOD = "caller answered the account questions";
    private static final String ADMIN_FLOW_REASSIGN_REASON = "Move this delivery to the available worker";
    private static final Pattern ADMIN_FLOW_REFERENCE = Pattern.compile("TEST-[0-9]+");

    private Session adminFlowDeliveryAdmin;
    private String adminFlowUnreturnedReference;
    private String adminFlowReminderReference;
    private String adminFlowCancelledReference;
    private long adminFlowCancelledInstructionID;
    private String adminFlowReplacementReference;
    private String adminFlowHeldReference;
    private String adminFlowHolder;
    private String adminFlowSecondWorker;
    private String adminFlowThirdWorker;

    @Autowired
    private PlatformTransactionManager adminFlowDeliveryTransactionManager;

    // What it does: Opens the Stage 7 Admin session after the earlier stages.
    // Input: The initialized flow database. Output: A signed-in Admin client.
    @BeforeAll
    void adminOpenDeliveryFlowSession() {
        adminFlowDeliveryAdmin = admin();
    }

    // ===== SECTION 2: DELIVERY PLAN CASES =====

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Checks an unreturned failure.
    // Input: A one-attempt fixture without a request.
    // Output: Failed attempt, flags and return state.
    @Test
    @DisplayName("S7-T01")
    void checkS7T01() {
        String reference = adminCreateFailedFlowCase(1, false, 0, "NONE");
        adminFlowUnreturnedReference = reference;
        JsonNode row = adminReadFailedFlowCase(reference);
        assertEquals(1, row.path(ADMIN_FAILED_PREFIX + "FailureCount").asInt());
        assertEquals("CLIENT_NOT_HOME", row.path(ADMIN_FAILED_PREFIX + "LatestFailureReason").asText());
        assertEquals("Nobody answered the door", row.path(ADMIN_FAILED_PREFIX + "LatestInternalNote").asText());
        assertFalse(row.path(ADMIN_FAILED_PREFIX + "LatestWorkerUsername").asText().isBlank());
        assertFalse(row.path(ADMIN_FAILED_PREFIX + "IsReturned").asBoolean());
        assertEquals("NONE", row.path(ADMIN_FAILED_PREFIX + "WaitingFlag").asText());
        assertFalse(row.path(ADMIN_FAILED_PREFIX + "IsRepeatedFailure").asBoolean());
        reportPartial("Rendered labels and absent action buttons (S7-H02).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Checks a return without a request.
    // Input: A fixture returned one day ago.
    // Output: Return age, flags and pending state.
    @Test
    @DisplayName("S7-T02")
    void checkS7T02() {
        JsonNode row = adminReadFailedFlowCase(adminCreateFailedFlowCase(1, true, 1, "NONE"));
        assertTrue(row.path(ADMIN_FAILED_PREFIX + "IsReturned").asBoolean());
        assertEquals(1, row.path(ADMIN_FAILED_PREFIX + "DaysSinceReturn").asInt());
        assertTrue(row.path(ADMIN_FAILED_PREFIX + "PendingInstructionID").isNull());
        assertEquals("NONE", row.path(ADMIN_FAILED_PREFIX + "WaitingFlag").asText());
        assertFalse(row.path(ADMIN_FAILED_PREFIX + "IsRepeatedFailure").asBoolean());
        reportPartial("Rendered return age and action labels (S7-H02).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Checks flag thresholds and the filtered queue.
    // Input: Five fixtures at the review thresholds.
    // Output: Review flags and matching rows.
    @Test
    @DisplayName("S7-T03")
    void checkS7T03() {
        List<String> references = List.of(
                adminCreateFailedFlowCase(2, true, 1, "NONE"),
                adminCreateFailedFlowCase(1, true, 3, "NONE"),
                adminCreateFailedFlowCase(1, true, 7, "NONE"),
                adminCreateFailedFlowCase(2, true, 8, "NONE"),
                adminCreateFailedFlowCase(1, true, 8, "REDELIVER"));
        adminFlowReminderReference = references.get(2);
        JsonNode checkS7T03Settings = adminFlowDeliveryAdmin.get(ADMIN_FAILED_CASES).expect(200).json();
        assertEquals(2, checkS7T03Settings.path(ADMIN_FAILED_PREFIX + "ReviewFailureCount").asInt());
        assertEquals(3, checkS7T03Settings.path(ADMIN_FAILED_PREFIX + "ReminderDays").asInt());
        assertEquals(7, checkS7T03Settings.path(ADMIN_FAILED_PREFIX + "FinalReviewDays").asInt());
        List<String> flags = List.of("NONE", "REMINDER_DUE", "FINAL_REVIEW", "FINAL_REVIEW", "NONE");
        List<String> flagged = adminReadFlowPages(ADMIN_FAILED_CASES + "?adminSearchFailedDeliveryCaseFilter=FLAGGED",
                ADMIN_FAILED_PREFIX).stream().map(row -> row.path("orderReference").asText()).toList();
        for (int index = 0; index < references.size(); index++) {
            JsonNode row = adminReadFailedFlowCase(references.get(index));
            assertEquals(flags.get(index), row.path(ADMIN_FAILED_PREFIX + "WaitingFlag").asText());
            assertEquals(index == 0 || index == 3, row.path(ADMIN_FAILED_PREFIX + "IsRepeatedFailure").asBoolean());
            assertEquals(index < 4, flagged.contains(references.get(index)));
        }
        reportPartial("Settings line and selected filter after browser reload (S7-H03).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Checks that viewing cases does not change order status.
    // Input: Three reads of the failed-case queue.
    // Output: Identical synthetic order totals.
    @Test
    @DisplayName("S7-T04")
    void checkS7T04() {
        String sql = """
                SELECT order_status, count(*) FROM customer_order
                WHERE order_reference LIKE 'TEST-%' GROUP BY order_status ORDER BY order_status
                """;
        var before = database.sql(sql).query().listOfRows();
        for (int reload = 0; reload < 3; reload++) {
            adminFlowDeliveryAdmin.get(ADMIN_FAILED_CASES).expect(200);
        }
        assertEquals(before, database.sql(sql).query().listOfRows());
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Sends a reminder and checks the cooldown.
    // Input: The seven-day return from S7-T03.
    // Output: One notification, saved times and a duplicate conflict.
    @Test
    @DisplayName("S7-T05")
    void checkS7T05() {
        long orderID = adminReadFlowOrderID(adminFlowReminderReference);
        String path = ADMIN_FAILED_CASES + "/" + orderID + "/reminder";
        assertFalse(adminFlowDeliveryAdmin.post(path, null).expect(200).json().path("message").asText().isBlank());
        JsonNode row = adminReadFailedFlowCase(adminFlowReminderReference);
        Instant last = Instant.parse(row.path(ADMIN_FAILED_PREFIX + "LastReminderTime").asText());
        Instant next = Instant.parse(row.path(ADMIN_FAILED_PREFIX + "NextReminderTime").asText());
        assertEquals(Duration.ofHours(24), Duration.between(last, next));
        adminCheckFlowNotification(adminFlowReminderReference, "CLIENT_DELIVERY_REMINDER", null);
        adminFlowDeliveryAdmin.post(path, null).expect(409).code("ADMIN_REMINDER_TOO_SOON");
        reportPartial("Toast dismissal, disabled reminder and cooldown line (S7-H04).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Checks that an unreturned package cannot trigger a reminder.
    // Input: The unreturned order from S7-T01.
    // Output: The required return conflict.
    @Test
    @DisplayName("S7-T06")
    void checkS7T06() {
        adminFlowDeliveryAdmin.post(ADMIN_FAILED_CASES + "/" + adminReadFlowOrderID(adminFlowUnreturnedReference)
                + "/reminder", null).expect(409).code("ADMIN_RETURN_NOT_CONFIRMED");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Approves a redelivery request and reads the saved order.
    // Input: A returned order with a tracking-page request.
    // Output: Approval, timeline, unchanged stock and notification.
    @Test
    @DisplayName("S7-T07")
    void checkS7T07() {
        String reference = adminCreateFailedFlowCase(1, true, 1, "REDELIVER");
        JsonNode row = adminReadFailedFlowCase(reference);
        long instructionID = row.path(ADMIN_FAILED_PREFIX + "PendingInstructionID").asLong();
        assertEquals("REDELIVER", row.path(ADMIN_FAILED_PREFIX + "PendingInstructionType").asText());
        assertEquals("TRACKING_PAGE", row.path(ADMIN_FAILED_PREFIX + "PendingInstructionSource").asText());
        Instant.parse(row.path(ADMIN_FAILED_PREFIX + "PendingInstructionSubmittedTime").asText());
        var before = adminReadFlowStock();
        JsonNode saved = adminApproveFlowRedelivery(adminFlowDeliveryAdmin, instructionID).expect(200).json();
        JsonNode order = adminReadFlowOrder(reference);
        JsonNode request = adminReadFlowInstruction(order, instructionID);
        adminCheckFlowDecision(saved, "READY_FOR_REDELIVERY", request.path("resolvedTime").asText());
        assertEquals("READY_FOR_REDELIVERY", order.path("orderStatus").asText());
        assertEquals("APPROVED", request.path("status").asText());
        assertEquals(bootstrapUsername, request.path("adminViewOrderClientInstructionResolvedByUsername").asText());
        assertTrue(adminReadFlowRows(order, "adminViewOrderTimelineList").stream().anyMatch(
                entry -> entry.toString().contains("Request approved.")));
        assertTrue(adminReadFlowPages(ADMIN_FAILED_CASES, ADMIN_FAILED_PREFIX).stream().noneMatch(
                entry -> reference.equals(entry.path("orderReference").asText())));
        assertEquals(before, adminReadFlowStock());
        adminCheckFlowNotification(reference, "CLIENT_REDELIVERY_APPROVED", null);
        reportPartial("Approval window, retained row and order page rendering (S7-H05).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Approves cancellation and checks the refund and stock.
    // Input: A paid returned order with a cancellation request.
    // Output: A final cancellation, matching refund and released reservations.
    @Test
    @DisplayName("S7-T08")
    void checkS7T08() {
        String reference = adminCreateFailedFlowCase(1, true, 1, "CANCEL");
        adminFlowCancelledReference = reference;
        adminFlowCancelledInstructionID = adminReadPendingFlowInstruction(reference);
        adminCheckPaidFlowCancellation(reference, adminFlowCancelledInstructionID);
        reportPartial("Dangerous confirmation, retained row, toast, focus and rendered order cards (S7-H06).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Checks that a decided request cannot be approved again.
    // Input: The cancellation from S7-T08.
    // Output: A conflict and unchanged cancelled order.
    @Test
    @DisplayName("S7-T09")
    void checkS7T09() {
        adminApproveFlowRedelivery(adminFlowDeliveryAdmin, adminFlowCancelledInstructionID)
                .expect(409).code("ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT");
        assertEquals("CANCELLED", adminReadFlowOrder(adminFlowCancelledReference).path("orderStatus").asText());
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Rejects a request and reads the waiting queue again.
    // Input: A returned order with a redelivery request.
    // Output: Saved reason, pending state cleared and notification.
    @Test
    @DisplayName("S7-T10")
    void checkS7T10() {
        String reference = adminCreateFailedFlowCase(1, true, 1, "REDELIVER");
        long instructionID = adminReadPendingFlowInstruction(reference);
        String path = ADMIN_FLOW_INSTRUCTIONS + "/" + instructionID + "/reject";
        assertEquals(400, adminFlowDeliveryAdmin.post(path, Map.of("adminRejectClientInstructionReason", ""))
                .response().statusCode());
        String reason = "address could not be verified";
        JsonNode saved = adminFlowDeliveryAdmin.post(path, Map.of("adminRejectClientInstructionReason", reason))
                .expect(200).json();
        JsonNode order = adminReadFlowOrder(reference);
        JsonNode request = adminReadFlowInstruction(order, instructionID);
        adminCheckFlowDecision(saved, "NO_CLIENT_REQUEST", request.path("resolvedTime").asText());
        assertEquals("DELIVERY_FAILED", order.path("orderStatus").asText());
        assertEquals("REJECTED", request.path("status").asText());
        assertEquals(reason, request.path("resolutionNote").asText());
        assertTrue(adminReadFailedFlowCase(reference).path(ADMIN_FAILED_PREFIX + "PendingInstructionID").isNull());
        adminCheckFlowNotification(reference, "CLIENT_REQUEST_REJECTED", reason);
        reportPartial("Disabled confirmation, retained row and restored action buttons (S7-H07).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Refuses redelivery approval for a cancellation request.
    // Input: A returned order with a pending cancellation.
    // Output: The wrong-request-type conflict.
    @Test
    @DisplayName("S7-T11")
    void checkS7T11() {
        String reference = adminCreateFailedFlowCase(1, true, 1, "CANCEL");
        JsonNode row = adminReadFailedFlowCase(reference);
        assertEquals("CANCEL", row.path(ADMIN_FAILED_PREFIX + "PendingInstructionType").asText());
        adminApproveFlowRedelivery(adminFlowDeliveryAdmin, row.path(ADMIN_FAILED_PREFIX + "PendingInstructionID").asLong())
                .expect(409).code("ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT");
        reportPartial("Which action buttons the page renders (S7-H02).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Refuses approval before return is confirmed.
    // Input: An unreturned order with a synthetic redelivery request.
    // Output: The required return conflict.
    @Test
    @DisplayName("S7-T12")
    void checkS7T12() {
        String reference = adminCreateFailedFlowCase(1, false, 0, "REDELIVER");
        JsonNode row = adminReadFailedFlowCase(reference);
        assertFalse(row.path(ADMIN_FAILED_PREFIX + "IsReturned").asBoolean());
        adminApproveFlowRedelivery(adminFlowDeliveryAdmin, row.path(ADMIN_FAILED_PREFIX + "PendingInstructionID").asLong())
                .expect(409).code("ADMIN_RETURN_NOT_CONFIRMED");
        reportPartial("Waiting message and absence of approval controls (S7-H02).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Checks the shortage block and subsequent Warehouse cancellation.
    // Input: A returned case and a shortage report for that order.
    // Output: Blocked approval, cancelled order and rejected request.
    @Test
    @DisplayName("S7-T13")
    void checkS7T13() {
        String reference = adminCreateFailedFlowCase(1, true, 1, "REDELIVER");
        long instructionID = adminReadPendingFlowInstruction(reference);
        adminFlowDeliveryAdmin.tool("shortage-report", Map.of(
                "adminTemporaryCreateShortageReportOrderStatus", "PICKING",
                "adminTemporaryCreateShortageReportMissingQuantity", 1,
                "adminTemporaryCreateShortageReportOrderReference", reference)).expect(200);
        assertTrue(adminReadFailedFlowCase(reference).path(ADMIN_FAILED_PREFIX + "HasOpenShortageCase").asBoolean());
        adminApproveFlowRedelivery(adminFlowDeliveryAdmin, instructionID).expect(409).code("ADMIN_OPEN_SHORTAGE_CASE_BLOCKS");
        JsonNode shortage = adminReadFlowRows(adminReadFlowOrder(reference), "adminViewOrderShortageCaseList").getFirst();
        long shortageID = shortage.path("shortageCaseID").asLong();
        adminFlowDeliveryAdmin.post("/api/admin/shortage-cases/" + shortageID + "/cancel", Map.of(
                "adminDecideShortageCancelStockCorrectionIsConfirmed", true,
                "adminDecideShortageCancelReason", "Warehouse recorded the damaged stock")).expect(200);
        JsonNode order = adminReadFlowOrder(reference);
        JsonNode request = adminReadFlowInstruction(order, instructionID);
        assertEquals("CANCELLED", order.path("orderStatus").asText());
        assertEquals("REJECTED", request.path("status").asText());
        assertEquals("The order was cancelled because of a stock shortage.", request.path("resolutionNote").asText());
        reportPartial("Disabled approve control, blocked explanation and accessible cross-page links (S7-H08).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Records a client request after identity confirmation.
    // Input: A returned order with no request and an identity check method.
    // Output: Saved manual request, deciding Admin and notification.
    @Test
    @DisplayName("S7-T14")
    void checkS7T14() {
        String reference = adminCreateFailedFlowCase(1, true, 2, "NONE");
        String path = ADMIN_FAILED_CASES + "/" + adminReadFlowOrderID(reference) + "/client-instruction";
        adminFlowDeliveryAdmin.post(path, Map.of("adminRecordClientInstructionType", "REDELIVER",
                "adminRecordClientInstructionIdentityIsConfirmed", false,
                "adminRecordClientInstructionIdentityCheckMethod", ADMIN_FLOW_IDENTITY_METHOD))
                .expect(400).code("ADMIN_IDENTITY_NOT_CONFIRMED");
        JsonNode saved = adminRecordFlowRequest(adminFlowDeliveryAdmin, reference, "REDELIVER", null).expect(200).json();
        JsonNode row = adminReadFailedFlowCase(reference);
        JsonNode request = adminReadFlowInstruction(adminReadFlowOrder(reference),
                row.path(ADMIN_FAILED_PREFIX + "PendingInstructionID").asLong());
        assertEquals("REDELIVER", request.path("instructionType").asText());
        assertEquals("PENDING", request.path("status").asText());
        assertEquals("ADMIN_MANUAL", request.path("source").asText());
        assertEquals(userID(bootstrapUsername), request.path("recordedByUserID").asLong());
        adminCheckFlowDecision(saved, "NEEDS_DECISION", request.path("submittedTime").asText());
        adminCheckFlowNotification(reference, "CLIENT_REQUEST_RECORDED", "redelivery");
        reportPartial("Record window fields, notice, disabled confirmation and restored queue (S7-H09).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Replaces a pending request and rejects a stale replacement.
    // Input: Two sessions with the same existing request ID.
    // Output: Correct withdrawal and exactly one current request.
    @Test
    @DisplayName("S7-T15")
    void checkS7T15() {
        String reference = adminCreateFailedFlowCase(1, true, 1, "REDELIVER");
        adminFlowReplacementReference = reference;
        long original = adminReadPendingFlowInstruction(reference);
        adminRecordFlowRequest(adminFlowDeliveryAdmin, reference, "CANCEL", original).expect(200);
        long firstReplacement = adminReadPendingFlowInstruction(reference);
        JsonNode order = adminReadFlowOrder(reference);
        assertEquals(2, adminReadFlowRows(order, "adminViewOrderClientInstructionList").size());
        assertEquals("WITHDRAWN", adminReadFlowInstruction(order, original).path("status").asText());
        assertEquals("Replaced by a request recorded by Admin.",
                adminReadFlowInstruction(order, original).path("resolutionNote").asText());
        assertEquals("CANCEL", adminReadFlowInstruction(order, firstReplacement).path("instructionType").asText());
        assertEquals("ADMIN_MANUAL", adminReadFlowInstruction(order, firstReplacement).path("source").asText());
        Session otherTab = admin();
        adminRecordFlowRequest(otherTab, reference, "REDELIVER", firstReplacement).expect(200);
        long secondReplacement = adminReadPendingFlowInstruction(reference);
        adminCheckFlowStaleRequest(adminRecordFlowRequest(adminFlowDeliveryAdmin, reference, "REDELIVER", firstReplacement));
        assertEquals(secondReplacement, adminReadPendingFlowInstruction(reference));
        assertEquals(1, adminCountPendingFlowRequests(reference));
        reportPartial("Replacement notice, stale window and Refresh request button (S7-H10).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Records and approves a new cancellation after refreshing the request.
    // Input: The second tab's redelivery request in S7-T15.
    // Output: The same saved state, stock and notification as S7-T08.
    @Test
    @DisplayName("S7-T16")
    void checkS7T16() {
        long checkS7T16RedeliveryID = adminReadPendingFlowInstruction(adminFlowReplacementReference);
        adminRecordFlowRequest(adminFlowDeliveryAdmin, adminFlowReplacementReference, "CANCEL", checkS7T16RedeliveryID)
                .expect(200);
        long checkS7T16CancellationID = adminReadPendingFlowInstruction(adminFlowReplacementReference);
        JsonNode checkS7T16Order = adminReadFlowOrder(adminFlowReplacementReference);
        assertEquals("WITHDRAWN", adminReadFlowInstruction(checkS7T16Order, checkS7T16RedeliveryID)
                .path("status").asText());
        assertEquals("CANCEL", adminReadFlowInstruction(checkS7T16Order, checkS7T16CancellationID)
                .path("instructionType").asText());
        adminCheckPaidFlowCancellation(adminFlowReplacementReference, checkS7T16CancellationID);
        reportPartial("Recording the cancellation from the refreshed first-tab window and searching again.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Prevents two Admins from approving the same request.
    // Input: Two real sessions authenticated as separate Admins.
    // Output: One approval audit and a second-session conflict.
    @Test
    @DisplayName("S7-T17")
    void checkS7T17() {
        String[] secondAdmin = testUser(adminFlowDeliveryAdmin, "ADMIN");
        Session privateSession = browser();
        privateSession.login(secondAdmin[0], secondAdmin[1]).expect(200);
        privateSession.change(secondAdmin[1], "SeparateAdmin742Own", "SeparateAdmin742Own").expect(200);
        privateSession.login(secondAdmin[0], "SeparateAdmin742Own").expect(200);
        String reference = adminCreateFailedFlowCase(1, true, 1, "REDELIVER");
        long instructionID = adminReadPendingFlowInstruction(reference);
        String auditQuery = "SELECT count(*) FROM audit_log WHERE action = 'REDELIVERY_APPROVED'";
        long before = count(auditQuery);
        adminApproveFlowRedelivery(adminFlowDeliveryAdmin, instructionID).expect(200);
        Reply stale = adminApproveFlowRedelivery(privateSession, instructionID).expect(409)
                .code("ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT");
        assertEquals("This request was already handled or replaced. Please refresh the page.",
                stale.json().path("message").asText());
        assertEquals(before + 1, count(auditQuery));
        reportPartial("Submitting the stale approval window in a second browser context (S7-H11).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Creates a held package and checks its initial responsibility.
    // Input: A shipped fixture and an available Delivery Worker.
    // Output: One package held by its original responsible worker.
    @Test
    @DisplayName("S7-T18")
    void checkS7T18() {
        String message = adminFlowDeliveryAdmin.tool("held-package", Map.of(
                "adminTemporaryCreateHeldPackageOrderStatus", "SHIPPED")).expect(200).json().path("message").asText();
        adminFlowHeldReference = adminReadFlowReference(message);
        JsonNode row = adminReadHeldFlowPackage(adminFlowHeldReference);
        assertEquals("SHIPPED", row.path(ADMIN_HELD_PREFIX + "OrderStatus").asText());
        adminFlowHolder = row.path(ADMIN_HELD_PREFIX + "CurrentHandlerUsername").asText();
        assertFalse(adminFlowHolder.isBlank());
        assertEquals(adminFlowHolder, row.path(ADMIN_HELD_PREFIX + "ResponsibleWorkerUsername").asText());
        assertEquals(row.path("currentHandlerUserID"), row.path(ADMIN_HELD_PREFIX + "ResponsibleWorkerUserID"));
        adminFlowSecondWorker = testUser(adminFlowDeliveryAdmin, "DELIVERY_WORKER")[0];
        reportPartial("Rendered held package row (S7-H12).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Changes responsibility and verifies the package remains with its holder.
    // Input: The package and two Delivery Workers from S7-T18.
    // Output: History and notifications with unchanged physical state.
    @Test
    @DisplayName("S7-T19")
    void checkS7T19() {
        var before = adminReadFlowStock();
        JsonNode row = adminReadHeldFlowPackage(adminFlowHeldReference);
        long packageID = row.path("packageID").asLong();
        long holderID = userID(adminFlowHolder);
        long replacementID = userID(adminFlowSecondWorker);
        List<JsonNode> workload = adminReadFlowRows(adminFlowDeliveryAdmin.get(ADMIN_HELD_PACKAGES + "/worker-workload")
                .expect(200).json(), "adminViewDeliveryWorkerWorkloadRowList");
        assertTrue(workload.stream().anyMatch(worker -> replacementID == worker.path("userID").asLong()
                && worker.path("adminViewDeliveryWorkerWorkloadHeldPackageCount").asInt() == 0));
        var packageBefore = adminReadFlowPackageCustody(packageID);
        JsonNode saved = adminReassignFlowPackage(packageID, replacementID).expect(200).json();
        assertEquals(adminFlowSecondWorker, saved.path("adminDecidedRowWorkerUsername").asText());
        assertEquals(bootstrapUsername, saved.path("adminDecidedRowDecidedByUsername").asText());
        assertTrue(saved.path("message").asText().contains("only after Warehouse confirms its return"));
        row = adminReadHeldFlowPackage(adminFlowHeldReference);
        assertEquals(holderID, row.path("currentHandlerUserID").asLong());
        assertEquals(replacementID, row.path(ADMIN_HELD_PREFIX + "ResponsibleWorkerUserID").asLong());
        assertEquals("SHIPPED", row.path(ADMIN_HELD_PREFIX + "OrderStatus").asText());
        assertEquals(packageBefore, adminReadFlowPackageCustody(packageID));
        List<Map<String, Object>> history = adminReadFlowReassignments(packageID);
        assertEquals(1, history.size());
        assertEquals(holderID, history.getFirst().get("previous_worker_user_id"));
        assertEquals(replacementID, history.getFirst().get("new_worker_user_id"));
        assertEquals(userID(bootstrapUsername), history.getFirst().get("assigned_by_admin_id"));
        assertEquals(ADMIN_FLOW_REASSIGN_REASON, history.getFirst().get("reason"));
        assertEquals(Instant.parse(saved.path("adminDecidedRowDecidedTime").asText()),
                Instant.parse(row.path(ADMIN_HELD_PREFIX + "LatestReassignmentTime").asText()));
        assertEquals(before, adminReadFlowStock());
        adminCheckFlowNotification(adminFlowHeldReference, "WORKER_DELIVERY_ASSIGNED", null, replacementID);
        adminCheckFlowNotification(adminFlowHeldReference, "WORKER_DELIVERY_REMOVED", null, holderID);
        reportPartial("Rendered worker options, custody notice and retained row (S7-H12).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Reassigns the same package again after the previous save.
    // Input: The package from S7-T19 and a third worker.
    // Output: Two ordered history rows and matching timeline entries.
    @Test
    @DisplayName("S7-T20")
    void checkS7T20() {
        adminFlowThirdWorker = testUser(adminFlowDeliveryAdmin, "DELIVERY_WORKER")[0];
        long packageID = adminReadHeldFlowPackage(adminFlowHeldReference).path("packageID").asLong();
        adminReassignFlowPackage(packageID, userID(adminFlowThirdWorker)).expect(200);
        var history = adminReadFlowReassignments(packageID);
        assertEquals(2, history.size());
        assertEquals(userID(adminFlowSecondWorker), history.getLast().get("previous_worker_user_id"));
        assertEquals(userID(adminFlowThirdWorker), history.getLast().get("new_worker_user_id"));
        assertEquals(2, adminReadFlowRows(adminReadFlowOrder(adminFlowHeldReference), "adminViewOrderTimelineList").stream()
                .filter(entry -> entry.toString().contains("Delivery responsibility moved from")).count());
        reportPartial("Rendered reassignment timeline and reloaded action control (S7-H12).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Refuses invalid and overlapping package reassignments.
    // Input: Held, Warehouse-held and row-locked packages.
    // Output: Three validation conflicts and a fast lock conflict with no side effects.
    @Test
    @DisplayName("S7-T21")
    void checkS7T21() {
        JsonNode row = adminReadHeldFlowPackage(adminFlowHeldReference);
        long packageID = row.path("packageID").asLong();
        adminReassignFlowPackage(packageID, userID(adminFlowThirdWorker)).expect(409).code("ADMIN_WORKER_NOT_ALLOWED");
        String warehouseWorker = testUser(adminFlowDeliveryAdmin, "WAREHOUSE_WORKER")[0];
        adminReassignFlowPackage(packageID, userID(warehouseWorker)).expect(409).code("ADMIN_WORKER_NOT_ALLOWED");
        long warehousePackageID = database.sql("""
                SELECT p.package_id FROM delivery_package p JOIN customer_order o USING (order_id)
                WHERE o.order_status = 'READY_FOR_PICKUP' ORDER BY p.package_id LIMIT 1
                """).query(Long.class).single();
        adminReassignFlowPackage(warehousePackageID, userID(adminFlowSecondWorker))
                .expect(409).code("ADMIN_PACKAGE_STATE_CONFLICT");

        long beforeHistory = count("SELECT count(*) FROM delivery_assignment_history");
        long beforeAudit = count("SELECT count(*) FROM audit_log WHERE action = 'DELIVERY_REASSIGNED'");
        long beforeNotifications = count("SELECT count(*) FROM notification WHERE type LIKE 'WORKER_DELIVERY_%'");
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(adminFlowDeliveryTransactionManager).executeWithoutResult(transaction -> {
                database.sql("SELECT package_id FROM delivery_package WHERE package_id = :packageID FOR UPDATE")
                        .param("packageID", packageID).query(Long.class).single();
                var request = executor.submit(() -> adminReassignFlowPackage(packageID, userID(adminFlowSecondWorker)));
                try {
                    Reply answer = request.get(6, TimeUnit.SECONDS).expect(409).code("ADMIN_PACKAGE_STATE_CONFLICT");
                    assertTrue(answer.json().path("message").asText().contains("refresh the page"));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("The reassignment lock check was interrupted.", failure);
                } catch (ExecutionException | TimeoutException failure) {
                    throw new AssertionError("Reassignment must return a conflict while the package lock is held.", failure);
                } finally {
                    transaction.setRollbackOnly();
                }
            });
        }
        assertEquals(beforeHistory, count("SELECT count(*) FROM delivery_assignment_history"));
        assertEquals(beforeAudit, count("SELECT count(*) FROM audit_log WHERE action = 'DELIVERY_REASSIGNED'"));
        assertEquals(beforeNotifications, count("SELECT count(*) FROM notification WHERE type LIKE 'WORKER_DELIVERY_%'"));
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Bans a holder and reassigns the still-held package.
    // Input: The original holder and a different active worker.
    // Output: Impact count, inactivity and successful reassignment.
    @Test
    @DisplayName("S7-T22")
    void checkS7T22() {
        long holderID = userID(adminFlowHolder);
        String userPath = USERS + "/" + holderID;
        assertEquals(1, adminFlowDeliveryAdmin.get(userPath + "/impact").expect(200).json()
                .path("adminPreviewUserImpactHeldPackageCount").asInt());
        adminFlowDeliveryAdmin.post(userPath + "/ban", Map.of("adminBanUserReason", "Suspend this test account")).expect(200);
        JsonNode row = adminReadHeldFlowPackage(adminFlowHeldReference);
        assertEquals("BANNED", row.path(ADMIN_HELD_PREFIX + "CurrentHandlerAccountStatus").asText());
        adminReassignFlowPackage(row.path("packageID").asLong(), userID(adminFlowSecondWorker)).expect(200);
        reportPartial("Rendered ban impact window and Worker not active flag (S7-H13).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Finds the six Stage 7 audit actions in the Logs API.
    // Input: The completed reminder, request and assignment cases.
    // Output: Matching rows and the saved identity check text.
    @Test
    @DisplayName("S7-T23")
    void checkS7T23() {
        for (String action : List.of("DELIVERY_REMINDER_SENT", "CLIENT_INSTRUCTION_RECORDED_BY_ADMIN",
                "REDELIVERY_APPROVED", "CANCELLATION_APPROVED", "CLIENT_INSTRUCTION_REJECTED", "DELIVERY_REASSIGNED")) {
            JsonNode answer = adminFlowDeliveryAdmin.get("/api/admin/logs/audit?adminSearchAuditLogAction=" + action)
                    .expect(200).json();
            List<JsonNode> rows = adminReadFlowRows(answer, "adminSearchAuditLogRowList");
            assertFalse(rows.isEmpty(), action);
            if ("CLIENT_INSTRUCTION_RECORDED_BY_ADMIN".equals(action)) {
                assertTrue(rows.stream().anyMatch(row -> row.toString().contains(ADMIN_FLOW_IDENTITY_METHOD)));
            }
        }
        reportPartial("Reading the matching entries and identity text in the Audit log tab.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Checks failed-delivery JSON, CSV and notifications for private data.
    // Input: Synthetic orders with marked private values.
    // Output: No private marker, plus a saved CSV audit.
    @Test
    @DisplayName("S7-T24")
    void checkS7T24() {
        assertTrue(count("SELECT count(*) FROM order_delivery_detail WHERE recipient_name LIKE 'PRIVATE%'") > 0);
        assertFalse(adminFlowDeliveryAdmin.get(ADMIN_FAILED_CASES).expect(200).body().contains("PRIVATE"));
        Reply download = adminFlowDeliveryAdmin.get(ADMIN_FAILED_CASES
                + "/csv?adminSearchFailedDeliveryCaseFilter=WAITING_FOR_RETURN").expect(200);
        assertTrue(download.header("Content-Disposition").contains("admin-failed-delivery-cases-"));
        assertFalse(download.body().contains("PRIVATE"));
        assertTrue(download.body().contains(adminFlowUnreturnedReference));
        assertFalse(download.body().contains(adminFlowReminderReference));
        assertEquals(0, count("SELECT count(*) FROM notification WHERE message LIKE '%PRIVATE%'"));
        assertTrue(count("SELECT count(*) FROM audit_log WHERE action = 'CSV_DOWNLOADED'"
                + " AND target_id = 'CSV_FAILED_DELIVERY_CASES'") > 0);
        reportPartial("Inspecting the response in browser DevTools and opening the saved download.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Verifies the Delivery cases route is protected.
    // Input: A signed-in Warehouse Worker.
    // Output: The shared access-denied error.
    @Test
    @DisplayName("S7-T25")
    void checkS7T25() {
        String[] worker = testUser(adminFlowDeliveryAdmin, "WAREHOUSE_WORKER");
        Session workerSession = browser();
        workerSession.login(worker[0], worker[1]).expect(200);
        workerSession.change(worker[1], "StageSevenPicker42Own", "StageSevenPicker42Own").expect(200);
        workerSession.login(worker[0], "StageSevenPicker42Own").expect(200);
        workerSession.get(ADMIN_FAILED_CASES).expect(403).code("SHARED_ACCESS_DENIED");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Refuses a stale manual request after another tab records the first request.
    // Input: Two sessions that originally found no pending request.
    // Output: Exactly one saved redelivery and a clear conflict.
    @Test
    @DisplayName("S7-T27")
    void checkS7T27() {
        String reference = adminCreateFailedFlowCase(1, true, 2, "NONE");
        assertTrue(adminReadFailedFlowCase(reference).path(ADMIN_FAILED_PREFIX + "PendingInstructionID").isNull());
        adminRecordFlowRequest(admin(), reference, "REDELIVER", null).expect(200);
        adminCheckFlowStaleRequest(adminRecordFlowRequest(adminFlowDeliveryAdmin, reference, "CANCEL", null));
        List<Map<String, Object>> requests = database.sql("""
                SELECT instruction_type, status FROM client_instruction WHERE order_id = :orderID ORDER BY instruction_id
                """).param("orderID", adminReadFlowOrderID(reference)).query().listOfRows();
        assertEquals(List.of(Map.of("instruction_type", "REDELIVER", "status", "PENDING")), requests);
        assertEquals("REDELIVER",
                adminReadFailedFlowCase(reference).path(ADMIN_FAILED_PREFIX + "PendingInstructionType").asText());
        reportPartial("Rendering the stale error and reopening with the latest request (S7-H10).");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Cancels an order with a zero-value accepted payment.
    // Input: The returned cancellation fixture with its payment set to zero.
    // Output: Unpaid decision, no refund row and an unpaid notification.
    @Test
    @DisplayName("S7-T28")
    void checkS7T28() {
        String reference = adminCreateFailedFlowCase(1, true, 1, "CANCEL");
        long orderID = adminReadFlowOrderID(reference);
        database.sql("UPDATE payment SET amount = 0 WHERE order_id = :orderID").param("orderID", orderID).update();
        JsonNode row = adminReadFailedFlowCase(reference);
        assertFalse(row.path(ADMIN_FAILED_PREFIX + "OrderIsPaid").asBoolean());
        JsonNode decision = adminFlowDeliveryAdmin.post(ADMIN_FLOW_INSTRUCTIONS + "/"
                + row.path(ADMIN_FAILED_PREFIX + "PendingInstructionID").asLong() + "/approve-cancellation",
                Map.of("adminApproveCancellationNote", "")).expect(200).json();
        assertEquals("CANCELLED", decision.path("adminDecidedRowStateWord").asText());
        assertTrue(decision.path("refundID").isNull());
        assertTrue(decision.path("adminDecidedRowRefundIsNew").isBoolean());
        assertFalse(decision.path("adminDecidedRowRefundIsNew").asBoolean());
        assertTrue(decision.path("message").asText().contains("Nothing had been paid, so no refund was needed."));
        assertEquals(0, database.sql("""
                SELECT count(*) FROM refund JOIN payment USING (payment_id) WHERE order_id = :orderID
                """).param("orderID", orderID).query(Long.class).single());
        String notification = adminCheckFlowNotification(reference, "CLIENT_CANCELLATION_APPROVED_UNPAID", null);
        assertFalse(notification.toLowerCase(Locale.ROOT).contains("refund"));
        reportPartial("Unpaid facts and success message as rendered in the browser.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Approves cancellation for a payment with a completed refund.
    // Input: A returned paid fixture and a synthetic REFUNDED row.
    // Output: The saved refund is reused without a new refund row or audit.
    @Test
    @DisplayName("S7-T29")
    void checkS7T29() {
        String checkS7T29Reference = adminCreateFailedFlowCase(1, true, 1, "CANCEL");
        long orderID = adminReadFlowOrderID(checkS7T29Reference);
        long instructionID = adminReadPendingFlowInstruction(checkS7T29Reference);
        long refundID = database.sql("""
                INSERT INTO refund (payment_id, amount, refund_status, completed_at)
                SELECT payment_id, amount, 'REFUNDED', now() FROM payment
                WHERE order_id = :orderID AND accepted_at IS NOT NULL
                RETURNING refund_id
                """).param("orderID", orderID).query(Long.class).single();
        JsonNode checkS7T29Decision = adminFlowDeliveryAdmin.post(ADMIN_FLOW_INSTRUCTIONS + "/" + instructionID
                + "/approve-cancellation", Map.of("adminApproveCancellationNote", "")).expect(200).json();
        assertEquals("Order " + checkS7T29Reference + " was cancelled. Refund " + refundID
                + " of this payment already existed, so no new refund was requested.",
                checkS7T29Decision.path("message").asText());
        assertEquals(refundID, checkS7T29Decision.path("refundID").asLong());
        assertTrue(checkS7T29Decision.path("adminDecidedRowRefundIsNew").isBoolean());
        assertFalse(checkS7T29Decision.path("adminDecidedRowRefundIsNew").asBoolean());

        JsonNode checkS7T29Order = adminReadFlowOrder(checkS7T29Reference);
        assertEquals("CANCELLED", checkS7T29Order.path("orderStatus").asText());
        assertEquals("APPROVED", adminReadFlowInstruction(checkS7T29Order, instructionID).path("status").asText());
        List<JsonNode> checkS7T29RefundList = adminReadFlowRows(checkS7T29Order, "adminViewOrderRefundList");
        assertEquals(1, checkS7T29RefundList.size());
        assertEquals("REFUNDED", checkS7T29RefundList.getFirst().path("refundStatus").asText());
        assertEquals(0, database.sql("""
                SELECT count(*) FROM audit_log WHERE action = 'REFUND_REQUESTED' AND target_id = :orderID
                """).param("orderID", Long.toString(orderID)).query(Long.class).single());
        assertEquals(refundID, database.sql("""
                SELECT (new_value ->> 'existingRefundID')::bigint FROM audit_log
                WHERE action = 'CANCELLATION_APPROVED' AND target_id = :instructionID
                """).param("instructionID", Long.toString(instructionID)).query(Long.class).single());
        adminCheckFlowNotification(checkS7T29Reference, "CLIENT_CANCELLATION_APPROVED_REFUND_EXISTS",
                "A refund for this order had already been started.");
        reportPartial("The reused-refund message and decided row as rendered in the browser.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // What it does: Rejects approval of instructions without the current return receipt.
    // Input: A redelivery with a missing link and a cancellation linked to an older event.
    // Output: Each approval returns the specified conflict and the request can still be rejected.
    @Test
    @DisplayName("S7-T30")
    void checkS7T30() {
        String checkS7T30RedeliveryReference = adminCreateFailedFlowCase(1, true, 1, "REDELIVER");
        long checkS7T30RedeliveryID = adminReadPendingFlowInstruction(checkS7T30RedeliveryReference);
        database.sql("UPDATE client_instruction SET return_custody_event_id = NULL WHERE instruction_id = :instructionID")
                .param("instructionID", checkS7T30RedeliveryID).update();
        JsonNode checkS7T30PendingRedelivery = adminReadFailedFlowCase(checkS7T30RedeliveryReference);
        adminCheckFlowReturnConflict(adminApproveFlowRedelivery(adminFlowDeliveryAdmin, checkS7T30RedeliveryID));
        assertEquals(checkS7T30PendingRedelivery, adminReadFailedFlowCase(checkS7T30RedeliveryReference));
        String checkS7T30RejectAddress = ADMIN_FLOW_INSTRUCTIONS + "/" + checkS7T30RedeliveryID + "/reject";
        adminFlowDeliveryAdmin.post(checkS7T30RejectAddress,
                Map.of("adminRejectClientInstructionReason", "The linked receipt is missing.")).expect(200);
        JsonNode checkS7T30Redelivery = adminReadFlowOrder(checkS7T30RedeliveryReference);
        assertEquals("DELIVERY_FAILED", checkS7T30Redelivery.path("orderStatus").asText());
        assertEquals("REJECTED", adminReadFlowInstruction(checkS7T30Redelivery, checkS7T30RedeliveryID)
                .path("status").asText());

        String checkS7T30CancellationReference = adminCreateFailedFlowCase(1, true, 1, "CANCEL");
        long checkS7T30CancellationID = adminReadPendingFlowInstruction(checkS7T30CancellationReference);
        assertEquals(1, database.sql("""
                INSERT INTO package_custody_event (package_id, event_type, actor_user_id, event_time)
                SELECT receipt.package_id, receipt.event_type, receipt.actor_user_id,
                       receipt.event_time + interval '1 second'
                FROM package_custody_event receipt JOIN client_instruction request
                    ON receipt.custody_event_id = request.return_custody_event_id
                WHERE request.instruction_id = :instructionID
                """).param("instructionID", checkS7T30CancellationID).update());
        JsonNode checkS7T30PendingCancellation = adminReadFailedFlowCase(checkS7T30CancellationReference);
        String checkS7T30CancelAddress = ADMIN_FLOW_INSTRUCTIONS + "/" + checkS7T30CancellationID
                + "/approve-cancellation";
        adminCheckFlowReturnConflict(adminFlowDeliveryAdmin.post(checkS7T30CancelAddress,
                Map.of("adminApproveCancellationNote", "")));
        assertEquals(checkS7T30PendingCancellation, adminReadFailedFlowCase(checkS7T30CancellationReference));
        JsonNode checkS7T30Cancellation = adminReadFlowOrder(checkS7T30CancellationReference);
        assertEquals("DELIVERY_FAILED", checkS7T30Cancellation.path("orderStatus").asText());
        assertTrue(adminReadFlowRows(checkS7T30Cancellation, "adminViewOrderRefundList").isEmpty());
        reportPartial("The retained failed-case row and approval error as rendered in the browser.");
    }

    // ===== SECTION 3: FIXTURES AND REQUESTS =====

    // What it does: Creates a fixture through the Stage 7 tool.
    // Input: Failure count, return state, return age and request type. Output: New order reference.
    private String adminCreateFailedFlowCase(int failures, boolean returned, int days, String request) {
        String message = adminFlowDeliveryAdmin.tool("failed-delivery-case", Map.of(
                "adminTemporaryCreateFailedDeliveryCaseFailureCount", failures,
                "adminTemporaryCreateFailedDeliveryCaseIsReturned", returned,
                "adminTemporaryCreateFailedDeliveryCaseDaysSinceReturn", days,
                "adminTemporaryCreateFailedDeliveryCaseClientRequest", request)).expect(200).json().path("message").asText();
        return adminReadFlowReference(message);
    }

    // What it does: Extracts a copyable order reference from a test-tool message.
    // Input: Tool message. Output: Reference of the created order.
    private String adminReadFlowReference(String message) {
        var match = ADMIN_FLOW_REFERENCE.matcher(message);
        assertTrue(match.find(), "The tool must display the test order reference.");
        return match.group();
    }

    // What it does: Submits an approval, manual request or reassignment using a live Admin session.
    // Input: The appropriate order, request or package key and the action fields. Output: HTTP reply.
    private Reply adminApproveFlowRedelivery(Session session, long instructionID) {
        return session.post(ADMIN_FLOW_INSTRUCTIONS + "/" + instructionID + "/approve-redelivery",
                Map.of("adminApproveRedeliveryNote", ""));
    }

    private Reply adminRecordFlowRequest(Session session, String reference, String type, Long expectedInstructionID) {
        var body = new HashMap<String, Object>();
        body.put("adminRecordClientInstructionType", type);
        body.put("adminRecordClientInstructionIdentityIsConfirmed", true);
        body.put("adminRecordClientInstructionIdentityCheckMethod", ADMIN_FLOW_IDENTITY_METHOD);
        body.put("adminRecordClientInstructionExpectedPendingInstructionID", expectedInstructionID);
        return session.post(ADMIN_FAILED_CASES + "/" + adminReadFlowOrderID(reference) + "/client-instruction", body);
    }

    private Reply adminReassignFlowPackage(long packageID, long workerID) {
        return adminFlowDeliveryAdmin.post(ADMIN_HELD_PACKAGES + "/" + packageID + "/reassign", Map.of(
                "adminReassignDeliveryNewWorkerUserID", workerID, "adminReassignDeliveryReason", ADMIN_FLOW_REASSIGN_REASON));
    }

    // What it does: Verifies the reply from an out-of-date record-request window.
    // Input: The second reply to use an old expected request ID. Output: Exact conflict and guidance.
    private void adminCheckFlowStaleRequest(Reply reply) {
        assertEquals("The pending request changed while this window was open. Refresh the request and check it again.",
                reply.expect(409).code("ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT").json().path("message").asText());
    }

    // What it does: Checks the refusal for an instruction whose return receipt is outdated or missing.
    // Input: The approval reply. Output: The exact conflict code and next options for Admin.
    private void adminCheckFlowReturnConflict(Reply reply) {
        assertEquals("This request belongs to an earlier return of the package, so it cannot be approved. "
                        + "Reject it or record a new request.",
                reply.expect(409).code("ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT").json().path("message").asText());
    }

    // ===== SECTION 4: REGRESSION ASSERTIONS =====

    // What it does: Checks the cancellation path shared by two paid fixtures.
    // Input: Reference and pending instruction ID. Output: Saved decision, refund, audit and stock checks.
    private void adminCheckPaidFlowCancellation(String reference, long instructionID) {
        String note = "client confirmed the request";
        assertTrue(adminReadFailedFlowCase(reference).path(ADMIN_FAILED_PREFIX + "OrderIsPaid").asBoolean());
        var stockBefore = adminReadFlowStock();
        JsonNode decision = adminFlowDeliveryAdmin.post(ADMIN_FLOW_INSTRUCTIONS + "/" + instructionID
                + "/approve-cancellation", Map.of("adminApproveCancellationNote", note)).expect(200).json();
        JsonNode order = adminReadFlowOrder(reference);
        JsonNode request = adminReadFlowInstruction(order, instructionID);
        adminCheckFlowDecision(decision, "CANCELLED", request.path("resolvedTime").asText());
        assertEquals("Order " + reference + " was cancelled. A full refund was requested.",
                decision.path("message").asText());
        assertEquals("CANCELLED", order.path("orderStatus").asText());
        assertEquals("APPROVED", request.path("status").asText());
        assertEquals(note, request.path("resolutionNote").asText());
        JsonNode payment = adminReadFlowRows(order, "adminViewOrderPaymentList").getFirst();
        JsonNode refund = adminReadFlowRows(order, "adminViewOrderRefundList").getFirst();
        assertEquals("ACCEPTED", payment.path("paymentStatus").asText());
        assertEquals("REQUESTED", refund.path("refundStatus").asText());
        assertEquals(payment.path("amount").decimalValue(), refund.path("amount").decimalValue());
        assertEquals(refund.path("refundID"), decision.path("refundID"));
        assertTrue(decision.path("adminDecidedRowRefundIsNew").asBoolean());
        assertTrue(adminReadFlowPages(ADMIN_FAILED_CASES, ADMIN_FAILED_PREFIX).stream().noneMatch(
                row -> reference.equals(row.path("orderReference").asText())));
        adminCheckFlowReleasedStock(adminReadFlowOrderID(reference), stockBefore);
        adminCheckFlowNotification(reference, "CLIENT_CANCELLATION_APPROVED", null);
        JsonNode audit = adminFlowDeliveryAdmin.get("/api/admin/logs/audit?adminSearchAuditLogAction=CANCELLATION_APPROVED"
                + "&adminSearchAuditLogTargetID=" + instructionID).expect(200).json();
        assertFalse(adminReadFlowRows(audit, "adminSearchAuditLogRowList").isEmpty());
    }

    // What it does: Verifies the in-place decision contract against the saved record.
    // Input: Action reply, expected state and timestamp from a subsequent read. Output: Matching state, actor and time.
    private void adminCheckFlowDecision(JsonNode reply, String state, String decidedTime) {
        assertEquals(state, reply.path("adminDecidedRowStateWord").asText());
        assertEquals(bootstrapUsername, reply.path("adminDecidedRowDecidedByUsername").asText());
        assertEquals(Instant.parse(decidedTime), Instant.parse(reply.path("adminDecidedRowDecidedTime").asText()));
    }

    // What it does: Verifies stock release for exactly the two order items.
    // Input: Cancelled order and complete stock snapshot before approval. Output: Exact post-cancellation stock.
    private void adminCheckFlowReleasedStock(long orderID, List<Map<String, Object>> before) {
        var quantities = new HashMap<String, Integer>();
        for (var item : database.sql("""
                SELECT sku, quantity FROM order_item JOIN product USING (product_id) WHERE order_id = :orderID
                """).param("orderID", orderID).query().listOfRows()) {
            quantities.put((String) item.get("sku"), (Integer) item.get("quantity"));
        }
        assertEquals(List.of(1, 3), quantities.values().stream().sorted().toList());
        List<Map<String, Object>> expected = new ArrayList<>();
        for (var stock : before) {
            Map<String, Object> adjusted = new HashMap<>(stock);
            adjusted.put("reserved_stock", (Integer) stock.get("reserved_stock")
                    - quantities.getOrDefault((String) stock.get("sku"), 0));
            expected.add(adjusted);
        }
        assertEquals(expected, adminReadFlowStock());
    }

    // What it does: Validates a newly saved notification for the placed order.
    // Input: Order reference, type, optional text and optional explicit worker recipient. Output: Stored public message.
    private String adminCheckFlowNotification(String reference, String type, String text, long... recipient) {
        long orderID = adminReadFlowOrderID(reference);
        long recipientID = recipient.length == 0 ? database.sql(
                "SELECT client_user_id FROM customer_order WHERE order_id = :orderID")
                .param("orderID", orderID).query(Long.class).single() : recipient[0];
        var notifications = database.sql("""
                SELECT recipient_user_id, order_id, type, message FROM notification
                ORDER BY notification_id DESC LIMIT 2
                """).query().listOfRows();
        var row = notifications.stream().filter(notification -> type.equals(notification.get("type")))
                .findFirst().orElseThrow(() -> new AssertionError("Missing new notification " + type));
        assertEquals(orderID, row.get("order_id"));
        assertEquals(recipientID, row.get("recipient_user_id"));
        String message = (String) row.get("message");
        assertTrue(message.contains(reference));
        assertFalse(message.contains("PRIVATE"));
        if (text != null) {
            assertTrue(message.contains(text));
        }
        return message;
    }

    // ===== SECTION 5: QUEUE AND DATABASE READS =====

    // What it does: Reads a failed case, its request ID, or its corresponding held row.
    // Input: Fixture reference. Output: Matching row or its pending ID.
    private JsonNode adminReadFailedFlowCase(String reference) {
        return adminReadFlowPages(ADMIN_FAILED_CASES, ADMIN_FAILED_PREFIX).stream()
                .filter(row -> reference.equals(row.path("orderReference").asText())).findFirst().orElseThrow();
    }

    private long adminReadPendingFlowInstruction(String reference) {
        long instructionID = adminReadFailedFlowCase(reference).path(ADMIN_FAILED_PREFIX + "PendingInstructionID").asLong();
        assertTrue(instructionID > 0);
        return instructionID;
    }

    private JsonNode adminReadHeldFlowPackage(String reference) {
        return adminReadFlowPages(ADMIN_HELD_PACKAGES, ADMIN_HELD_PREFIX).stream()
                .filter(row -> reference.equals(row.path(ADMIN_HELD_PREFIX + "OrderReference").asText()))
                .findFirst().orElseThrow();
    }

    // What it does: Follows the list's pagination and preserves its optional filter.
    // Input: Route and response field prefix. Output: All rows from the live API.
    private List<JsonNode> adminReadFlowPages(String address, String prefix) {
        var rows = new ArrayList<JsonNode>();
        String separator = address.contains("?") ? "&" : "?";
        int pageCount = 1;
        for (int page = 1; page <= pageCount; page++) {
            JsonNode reply = adminFlowDeliveryAdmin.get(address + separator + prefix + "PageNumber=" + page)
                    .expect(200).json();
            assertEquals(page, reply.path(prefix + "PageNumber").asInt());
            pageCount = reply.path(prefix + "TotalPageCount").asInt();
            assertTrue(pageCount >= 1);
            rows.addAll(adminReadFlowRows(reply, prefix + "RowList"));
        }
        return rows;
    }

    // What it does: Reads a named array of records from a live response.
    // Input: Response object and array field. Output: A list of JSON records.
    private List<JsonNode> adminReadFlowRows(JsonNode reply, String field) {
        JsonNode rows = reply.path(field);
        assertTrue(rows.isArray(), field);
        return StreamSupport.stream(rows.spliterator(), false).toList();
    }

    // What it does: Reads the order's API detail, internal ID or one displayed request.
    // Input: Order reference or loaded detail and instruction ID. Output: The matching detail or key.
    private JsonNode adminReadFlowOrder(String reference) {
        return adminFlowDeliveryAdmin.get("/api/admin/orders/" + adminReadFlowOrderID(reference)).expect(200).json();
    }

    private long adminReadFlowOrderID(String reference) {
        return database.sql("SELECT order_id FROM customer_order WHERE order_reference = :reference")
                .param("reference", reference).query(Long.class).single();
    }

    private JsonNode adminReadFlowInstruction(JsonNode order, long instructionID) {
        return adminReadFlowRows(order, "adminViewOrderClientInstructionList").stream()
                .filter(row -> row.path("instructionID").asLong() == instructionID).findFirst().orElseThrow();
    }

    // What it does: Counts pending requests after a replacement attempt.
    // Input: Fixture reference. Output: Number of pending requests for that order.
    private long adminCountPendingFlowRequests(String reference) {
        return database.sql("SELECT count(*) FROM client_instruction WHERE order_id = :orderID AND status = 'PENDING'")
                .param("orderID", adminReadFlowOrderID(reference)).query(Long.class).single();
    }

    // What it does: Reads the synthetic inventory in stable SKU order.
    // Input: The test database. Output: Physical and reserved balances before or after an action.
    private List<Map<String, Object>> adminReadFlowStock() {
        return database.sql("""
                SELECT p.sku, i.physical_stock, i.reserved_stock FROM product p JOIN inventory i USING (product_id)
                WHERE p.sku LIKE 'TEST-%' ORDER BY p.sku
                """).query().listOfRows();
    }

    // What it does: Reads the package's holder and physical custody.
    // Input: Package ID. Output: Handler key and custody word.
    private Map<String, Object> adminReadFlowPackageCustody(long packageID) {
        return database.sql("""
                SELECT current_handler_user_id, custody_status FROM delivery_package WHERE package_id = :packageID
                """).param("packageID", packageID).query().singleRow();
    }

    // What it does: Reads the package's responsibility history in saved order.
    // Input: Package ID. Output: Previous and new worker, Admin and reason for each reassignment.
    private List<Map<String, Object>> adminReadFlowReassignments(long packageID) {
        return database.sql("""
                SELECT previous_worker_user_id, new_worker_user_id, assigned_by_admin_id, reason
                FROM delivery_assignment_history WHERE package_id = :packageID ORDER BY assignment_id
                """).param("packageID", packageID).query().listOfRows();
    }
}
