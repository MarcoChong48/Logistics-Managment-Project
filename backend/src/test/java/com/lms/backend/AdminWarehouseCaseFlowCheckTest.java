// TEMPORARY TEST CODE - remove before integration
// Stage 6 checks Warehouse cases through the Admin API and the isolated flow database.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AdminWarehouseCaseFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: ADDRESSES AND FIXTURES =====

    private static final String ADMIN_SHORTAGE_CASES = "/api/admin/shortage-cases";
    private static final String ADMIN_SHORTAGE_CASE_FILTER = "adminSearchShortageCaseCaseStatus";
    private static final String ADMIN_SHORTAGE_CASE_ROWS = "adminSearchShortageCaseRowList";
    private static final String ADMIN_PICKING_TASKS = "/api/admin/picking-tasks";
    private static final String ADMIN_PICKING_TASK_ROWS = "adminSearchPickingTaskRowList";
    private static final String ADMIN_WORKLOAD_ROWS = "adminViewWarehouseWorkerWorkloadRowList";
    private static final String ADMIN_WAIT_REASON = "Wait for replacement stock";
    private static final String ADMIN_CANCEL_REASON = "Warehouse recorded the damaged stock";
    private static final String ADMIN_REASSIGN_REASON = "Move this task to the available worker";
    private static final Pattern ADMIN_SHORTAGE_TOOL_MESSAGE =
            Pattern.compile("^Created shortage case ([0-9]+) for order (TEST-[0-9]+)\\.$");
    private static final Pattern ADMIN_PICKING_TOOL_MESSAGE =
            Pattern.compile("^Created picking order (TEST-[0-9]+) for (test_\\w+)\\.$");
    private static final JsonMapper ADMIN_AUDIT_JSON = JsonMapper.builder().build();

    private JsonNode adminCheckInitialShortage;
    private JsonNode adminCheckPaidShortage;
    private JsonNode adminCheckPickingTask;
    private String adminCheckFirstWorkerUsername;
    private long adminCheckSecondWorkerID;

    @Autowired
    private PlatformTransactionManager adminCheckPickingOrderTransactionManager;

    // ===== SECTION 2: SHORTAGE CASES =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the report tool, the shortage queue and the linked order data.
    // What it does: Creates a picking shortage and checks its public case and timeline fields.
    // Input: An Admin session and the shortage-report fixture tool.
    // Output: One open shortage that reports one missing mug.
    @Test
    @DisplayName("S6-T01")
    void checkS6T01() {
        Session checkS6T01Admin = admin();
        adminCheckInitialShortage = adminCreateFlowShortage(checkS6T01Admin, "PICKING");
        assertEquals("OPEN", adminCheckInitialShortage.path("caseStatus").asText());
        assertEquals(1, adminCheckInitialShortage.path("missingQuantity").asInt());
        assertEquals(3, adminCheckInitialShortage.path("adminSearchShortageCaseOrderedQuantity").asInt());
        assertTrue(adminCheckInitialShortage.path("adminSearchShortageCaseSKU").asText().startsWith("TEST-MUG-"));
        assertFalse(adminCheckInitialShortage.path("adminSearchShortageCaseProductName").asText().isBlank());
        assertEquals("1 mug cracked on shelf", adminCheckInitialShortage.path("reportNote").asText());
        assertEquals(adminCheckInitialShortage.path("reportedByWorkerID").asLong(),
                userID(adminCheckInitialShortage.path("adminSearchShortageCaseReportedByUsername").asText()));
        Instant.parse(adminCheckInitialShortage.path("reportedTime").asText());

        // The order exposes the same shortage and includes the report in its timeline.
        JsonNode checkS6T01Order = adminReadFlowOrder(checkS6T01Admin, adminCheckInitialShortage);
        JsonNode checkS6T01Case = adminFindFlowRow(adminReadFlowRows(checkS6T01Order,
                "adminViewOrderShortageCaseList"), "shortageCaseID",
                adminCheckInitialShortage.path("shortageCaseID").asText());
        assertEquals(adminCheckInitialShortage.path("adminSearchShortageCaseSKU"),
                checkS6T01Case.path("adminViewOrderShortageCaseSKU"));
        assertEquals(adminCheckInitialShortage.path("reportedTime"), checkS6T01Case.path("reportedTime"));
        assertEquals(1, checkS6T01Case.path("missingQuantity").asInt());
        assertEquals("OPEN", checkS6T01Case.path("caseStatus").asText());
        assertTrue(checkS6T01Case.path("decision").isNull());
        assertTrue(adminReadFlowRows(checkS6T01Order, "adminViewOrderTimelineList").stream().anyMatch(
                checkS6T01Timeline -> checkS6T01Timeline.path("adminViewOrderTimelineText").asText()
                        .startsWith("Shortage reported")));
        checkS6T01Admin.get("/admin/admin-warehouse-cases.html").expect(200);
        reportPartial("Sample appearance, row controls, order link and rendered order table are checked in S6-H02.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks waiting and its client notice.
    // What it does: Puts the shortage on hold and reads the saved case, order and notification.
    // Input: The picking shortage from S6-T01.
    // Output: A waiting case, unchanged order status and one matching client notice.
    @Test
    @DisplayName("S6-T02")
    void checkS6T02() {
        Session checkS6T02Admin = admin();
        JsonNode checkS6T02Decision = adminWaitForFlowShortage(checkS6T02Admin, adminCheckInitialShortage)
                .expect(200).json();
        JsonNode checkS6T02Case = adminReadFlowShortage(checkS6T02Admin, "OPEN_AND_WAITING",
                adminCheckInitialShortage.path("shortageCaseID"));
        assertEquals("WAITING_RESTOCK", checkS6T02Case.path("caseStatus").asText());
        assertEquals("WAIT_FOR_RESTOCK", checkS6T02Case.path("decision").asText());
        assertEquals(bootstrapUsername, checkS6T02Case.path("adminSearchShortageCaseDecidedByUsername").asText());
        Instant.parse(checkS6T02Case.path("decidedTime").asText());
        assertEquals(ADMIN_WAIT_REASON, checkS6T02Case.path("decisionNote").asText());
        adminCheckFlowDecidedRow(checkS6T02Decision, "WAITING_RESTOCK", null,
                checkS6T02Case.path("decidedTime"), null);

        // Waiting preserves the order state and tells its own client about the delay.
        JsonNode checkS6T02Order = adminReadFlowOrder(checkS6T02Admin, checkS6T02Case);
        assertEquals("PICKING", checkS6T02Order.path("orderStatus").asText());
        adminCheckFlowNotification(adminReadFlowNotifications().getFirst(), checkS6T02Case,
                checkS6T02Order.path("clientUserID").asLong(), "CLIENT_SHORTAGE_WAITING");
        reportPartial("The retained row, its focus and the cancel button after reload are checked in S6-H03.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the Warehouse resolution handoff.
    // What it does: Resolves a waiting shortage and compares the default and resolved lists.
    // Input: The waiting shortage from S6-T02.
    // Output: One resolved case with a saved resolution time and no default queue match.
    @Test
    @DisplayName("S6-T03")
    void checkS6T03() {
        Session checkS6T03Admin = admin();
        JsonNode shortageCaseID = adminCheckInitialShortage.path("shortageCaseID");
        checkS6T03Admin.tool("shortage-resolved",
                Map.of("adminTemporaryResolveShortageCaseShortageCaseID", shortageCaseID.asLong())).expect(200);
        assertFalse(adminReadFlowShortages(checkS6T03Admin, "OPEN_AND_WAITING").stream().anyMatch(
                checkS6T03Row -> checkS6T03Row.path("shortageCaseID").equals(shortageCaseID)));
        JsonNode checkS6T03Resolved = adminReadFlowShortage(checkS6T03Admin, "RESOLVED", shortageCaseID);
        assertEquals("RESOLVED", checkS6T03Resolved.path("caseStatus").asText());
        Instant.parse(checkS6T03Resolved.path("resolvedTime").asText());
        reportPartial("Rendered actions and filter persistence across browser reloads are checked in S6-H04.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the server validation behind the cancel form.
    // What it does: Creates a paid shortage and submits cancellation without the stock correction tick.
    // Input: A ready-for-pickup order and a valid cancellation reason.
    // Output: A refused request and the unchanged open case.
    @Test
    @DisplayName("S6-T04")
    void checkS6T04() {
        Session checkS6T04Admin = admin();
        adminCheckPaidShortage = adminCreateFlowShortage(checkS6T04Admin, "READY_FOR_PICKUP");
        adminCancelFlowShortage(checkS6T04Admin, adminCheckPaidShortage, false)
                .expect(400).code("ADMIN_STOCK_CORRECTION_NOT_CONFIRMED");
        assertEquals("OPEN", adminReadFlowShortage(checkS6T04Admin, "OPEN",
                adminCheckPaidShortage.path("shortageCaseID")).path("caseStatus").asText());
        reportPartial("The tick and short-reason button behavior are checked in S6-H05.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks paid cancellation and its inventory, refund and notification effects.
    // What it does: Cancels the paid order and compares the order view, inventory and movement rows.
    // Input: The untouched ready-for-pickup shortage from S6-T04.
    // Output: One final cancellation, a full refund request and released reservations.
    @Test
    @DisplayName("S6-T05")
    void checkS6T05() {
        Session checkS6T05Admin = admin();
        List<Map<String, Object>> checkS6T05StockBefore = adminReadFlowStock();
        JsonNode checkS6T05Decision = adminCancelFlowShortage(checkS6T05Admin, adminCheckPaidShortage, true)
                .expect(200).json();
        JsonNode checkS6T05Case = adminReadFlowShortage(checkS6T05Admin, "CANCELLED",
                adminCheckPaidShortage.path("shortageCaseID"));
        assertEquals("Order " + adminReadFlowShortageReference(adminCheckPaidShortage)
                + " was cancelled. A full refund was requested.",
                checkS6T05Decision.path("message").asText());
        assertEquals("CANCELLED", checkS6T05Case.path("caseStatus").asText());

        // Check the same payment and refund data the order page displays.
        JsonNode checkS6T05Order = adminReadFlowOrder(checkS6T05Admin, adminCheckPaidShortage);
        assertEquals("CANCELLED", checkS6T05Order.path("orderStatus").asText());
        List<JsonNode> checkS6T05Payments = adminReadFlowRows(checkS6T05Order, "adminViewOrderPaymentList");
        List<JsonNode> checkS6T05Refunds = adminReadFlowRows(checkS6T05Order, "adminViewOrderRefundList");
        assertEquals(1, checkS6T05Payments.size());
        assertEquals(1, checkS6T05Refunds.size());
        JsonNode checkS6T05Payment = checkS6T05Payments.getFirst();
        JsonNode checkS6T05Refund = checkS6T05Refunds.getFirst();
        assertEquals("ACCEPTED", checkS6T05Payment.path("paymentStatus").asText());
        assertEquals("REQUESTED", checkS6T05Refund.path("refundStatus").asText());
        assertEquals(checkS6T05Payment.path("paymentID"), checkS6T05Refund.path("paymentID"));
        assertEquals(0, checkS6T05Payment.path("amount").decimalValue()
                .compareTo(checkS6T05Refund.path("amount").decimalValue()));
        adminCheckFlowDecidedRow(checkS6T05Decision, "CANCELLED", null,
                checkS6T05Case.path("decidedTime"), checkS6T05Refund.path("refundID").asLong());

        // Only the two reservations of this order lower the balance.
        adminCheckFlowStockRelease(checkS6T05Order.path("orderID").asLong(), checkS6T05StockBefore);
        List<Map<String, Object>> checkS6T05Movements = database.sql("""
                SELECT movement_type, quantity_delta, reserved_delta
                FROM stock_movement ORDER BY movement_id DESC LIMIT 2
                """).query().listOfRows();
        assertEquals(2, checkS6T05Movements.size());
        assertTrue(checkS6T05Movements.stream().allMatch(checkS6T05Movement ->
                "RESERVATION_RELEASED".equals(checkS6T05Movement.get("movement_type"))
                        && Integer.valueOf(0).equals(checkS6T05Movement.get("quantity_delta"))));
        assertEquals(List.of(-3, -1), checkS6T05Movements.stream()
                .map(checkS6T05Movement -> (Integer) checkS6T05Movement.get("reserved_delta")).sorted().toList());
        adminCheckFlowNotification(adminReadFlowNotifications().getFirst(), adminCheckPaidShortage,
                checkS6T05Order.path("clientUserID").asLong(), "CLIENT_SHORTAGE_CANCELLED");
        reportPartial("The rendered confirmation facts, result message and order cards are checked in S6-H05.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks that replaying a handled shortage cannot request another refund.
    // What it does: Repeats the paid cancellation and counts that order's refund rows.
    // Input: The cancellation completed in S6-T05.
    // Output: A case-state conflict and exactly one saved refund.
    @Test
    @DisplayName("S6-T06")
    void checkS6T06() {
        adminCancelFlowShortage(admin(), adminCheckPaidShortage, true)
                .expect(409).code("ADMIN_SHORTAGE_CASE_STATE_CONFLICT");
        assertEquals(1, adminCountFlowRefunds(adminCheckPaidShortage.path("orderID").asLong()));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks that a waiting case may still be cancelled.
    // What it does: Waits on a new picking shortage and cancels it through the live API.
    // Input: A paid picking order with one open assignment.
    // Output: A cancelled order and task, with the assignment ended.
    @Test
    @DisplayName("S6-T07")
    void checkS6T07() {
        Session checkS6T07Admin = admin();
        JsonNode checkS6T07Shortage = adminCreateFlowShortage(checkS6T07Admin, "PICKING");
        adminWaitForFlowShortage(checkS6T07Admin, checkS6T07Shortage).expect(200);
        adminCancelFlowShortage(checkS6T07Admin, checkS6T07Shortage, true).expect(200);
        assertEquals("CANCELLED", adminReadFlowOrder(checkS6T07Admin, checkS6T07Shortage).path("orderStatus").asText());

        // A worker can no longer hold the picking task once the order ends.
        Map<String, Object> checkS6T07Assignment = database.sql("""
                SELECT task.task_status, assignment.ended_at
                FROM picking_task task JOIN picking_assignment_history assignment USING (picking_task_id)
                WHERE task.order_id = :orderID
                """).param("orderID", checkS6T07Shortage.path("orderID").asLong()).query().singleRow();
        assertEquals("CANCELLED", checkS6T07Assignment.get("task_status"));
        assertNotNull(checkS6T07Assignment.get("ended_at"));
        reportPartial("The availability of the cancel control after reload is checked in S6-H03.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks cancellation without an accepted payment.
    // What it does: Cancels an unpaid shortage and reads its order and client notification.
    // Input: An order awaiting payment.
    // Output: A final cancellation with no refund row or refund promise.
    @Test
    @DisplayName("S6-T08")
    void checkS6T08() {
        Session checkS6T08Admin = admin();
        JsonNode checkS6T08Shortage = adminCreateFlowShortage(checkS6T08Admin, "AWAITING_PAYMENT");
        assertFalse(checkS6T08Shortage.path("adminSearchShortageCaseOrderIsPaid").asBoolean(true));
        JsonNode checkS6T08Decision = adminCancelFlowShortage(checkS6T08Admin, checkS6T08Shortage, true)
                .expect(200).json();
        JsonNode checkS6T08Case = adminReadFlowShortage(checkS6T08Admin, "CANCELLED",
                checkS6T08Shortage.path("shortageCaseID"));
        assertEquals("Order " + adminReadFlowShortageReference(checkS6T08Shortage)
                + " was cancelled. Nothing had been paid, so no refund was needed.",
                checkS6T08Decision.path("message").asText());
        adminCheckFlowDecidedRow(checkS6T08Decision, "CANCELLED", null, checkS6T08Case.path("decidedTime"), null);
        JsonNode checkS6T08Order = adminReadFlowOrder(checkS6T08Admin, checkS6T08Shortage);
        assertEquals("CANCELLED", checkS6T08Order.path("orderStatus").asText());
        assertTrue(adminReadFlowRows(checkS6T08Order, "adminViewOrderRefundList").isEmpty());

        // The unpaid notification is addressed to the fixture client and gives only the payment facts.
        Map<String, Object> checkS6T08Notification = adminReadFlowNotifications().getFirst();
        adminCheckFlowNotification(checkS6T08Notification, checkS6T08Shortage,
                checkS6T08Order.path("clientUserID").asLong(), "CLIENT_SHORTAGE_CANCELLED_UNPAID");
        assertFalse(checkS6T08Notification.get("message").toString().contains("refund"));
        reportPartial("The unpaid action text, modal facts and empty refund box are checked in S6-H06.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks multiple shortages on one order and the per-product uniqueness rule.
    // What it does: Adds a pen shortage, repeats the report, then cancels the first case.
    // Input: One picking order with a mug shortage and a pen item.
    // Output: One duplicate conflict and two cancelled cases with a linked closure note.
    @Test
    @DisplayName("S6-T09")
    void checkS6T09() {
        Session checkS6T09Admin = admin();
        JsonNode checkS6T09First = adminCreateFlowShortage(checkS6T09Admin, "PICKING");
        Map<String, Object> checkS6T09Request = Map.of(
                "adminTemporaryCreateShortageReportOrderStatus", "PICKING",
                "adminTemporaryCreateShortageReportMissingQuantity", 1,
                "adminTemporaryCreateShortageReportOrderReference", adminReadFlowShortageReference(checkS6T09First));
        JsonNode checkS6T09Second = adminReadCreatedFlowShortage(checkS6T09Admin,
                checkS6T09Admin.tool("shortage-report", checkS6T09Request).expect(200));
        assertEquals(checkS6T09First.path("orderID"), checkS6T09Second.path("orderID"));
        assertTrue(checkS6T09Second.path("adminSearchShortageCaseSKU").asText().startsWith("TEST-PEN-"));
        assertNotEquals(checkS6T09First.path("shortageCaseID"), checkS6T09Second.path("shortageCaseID"));
        checkS6T09Admin.tool("shortage-report", checkS6T09Request)
                .expect(409).code("ADMIN_SHORTAGE_CASE_STATE_CONFLICT");

        // Cancelling the order also closes the pen case with the first case number in the note.
        adminCancelFlowShortage(checkS6T09Admin, checkS6T09First, true).expect(200);
        JsonNode checkS6T09CancelledFirst = adminReadFlowShortage(checkS6T09Admin, "CANCELLED",
                checkS6T09First.path("shortageCaseID"));
        JsonNode checkS6T09CancelledSecond = adminReadFlowShortage(checkS6T09Admin, "CANCELLED",
                checkS6T09Second.path("shortageCaseID"));
        assertEquals("CANCELLED", checkS6T09CancelledFirst.path("caseStatus").asText());
        assertEquals("CANCELLED", checkS6T09CancelledSecond.path("caseStatus").asText());
        assertEquals("Closed because the order was cancelled in case "
                + checkS6T09First.path("shortageCaseID").asLong() + ".",
                checkS6T09CancelledSecond.path("decisionNote").asText());
    }

    // ===== SECTION 3: PICKING ASSIGNMENTS =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the tool and the live picking queue.
    // What it does: Creates a picking fixture and a second worker.
    // Input: The sample builder and an empty optional worker username.
    // Output: A listed task with a named worker, a start time and one picked item out of four.
    @Test
    @DisplayName("S6-T10")
    void checkS6T10() {
        Session checkS6T10Admin = admin();
        String checkS6T10Message = checkS6T10Admin.tool("picking-order", Map.of()).expect(200)
                .json().path("message").asText();
        var checkS6T10Match = ADMIN_PICKING_TOOL_MESSAGE.matcher(checkS6T10Message);
        assertTrue(checkS6T10Match.matches(), "The tool must return the order reference and worker username.");
        adminCheckFirstWorkerUsername = checkS6T10Match.group(2);
        adminCheckSecondWorkerID = userID(testUser(checkS6T10Admin, "WAREHOUSE_WORKER")[0]);
        adminCheckPickingTask = adminFindFlowRow(adminReadFlowPickingTasks(checkS6T10Admin),
                "adminSearchPickingTaskOrderReference", checkS6T10Match.group(1));
        assertEquals(adminCheckFirstWorkerUsername,
                adminCheckPickingTask.path("adminSearchPickingTaskCurrentWorkerUsername").asText());
        Instant.parse(adminCheckPickingTask.path("startTime").asText());
        assertEquals(1, adminCheckPickingTask.path("adminSearchPickingTaskPickedQuantityTotal").asInt());
        assertEquals(4, adminCheckPickingTask.path("adminSearchPickingTaskOrderedQuantityTotal").asInt());
        reportPartial("The rendered picking row and progress text are checked in S6-H07.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks reassignment without rewriting the saved work.
    // What it does: Reads workloads and reassigns the task, then compares progress, scans and stock.
    // Input: The picking task and two workers from S6-T10.
    // Output: One ended assignment and one open assignment with the original progress.
    @Test
    @DisplayName("S6-T11")
    void checkS6T11() {
        Session checkS6T11Admin = admin();
        long checkS6T11FirstWorkerID = userID(adminCheckFirstWorkerUsername);
        JsonNode checkS6T11Workload = checkS6T11Admin.get(ADMIN_PICKING_TASKS + "/worker-workload").expect(200).json();
        List<JsonNode> checkS6T11Workers = adminReadFlowRows(checkS6T11Workload, ADMIN_WORKLOAD_ROWS);
        assertTrue(checkS6T11Workers.stream().anyMatch(checkS6T11Worker ->
                checkS6T11Worker.path("userID").asLong() == adminCheckSecondWorkerID
                        && checkS6T11Worker.path("adminViewWarehouseWorkerWorkloadActiveTaskCount").asInt() == 0));
        assertTrue(checkS6T11Workers.stream().anyMatch(checkS6T11Worker ->
                checkS6T11Worker.path("userID").asLong() == checkS6T11FirstWorkerID
                        && checkS6T11Worker.path("adminViewWarehouseWorkerWorkloadActiveTaskCount").asInt() == 1));

        // The order's accepted scan and the inventory balances must remain as they were.
        List<JsonNode> checkS6T11Scans = adminReadFlowScans(checkS6T11Admin, adminCheckPickingTask);
        List<Map<String, Object>> checkS6T11Stock = adminReadFlowStock();
        assertEquals(1, checkS6T11Scans.size());
        assertEquals(adminCheckFirstWorkerUsername,
                checkS6T11Scans.getFirst().path("adminSearchPickingScanHistoryWorkerUsername").asText());

        // A second writer gets a conflict while the order is locked, and can try again after the lock is released.
        List<Map<String, Object>> checkS6T11AssignmentsBefore = adminReadFlowAssignments();
        new TransactionTemplate(adminCheckPickingOrderTransactionManager).executeWithoutResult(
                checkS6T11TransactionStatus -> {
                    database.sql("SELECT order_id FROM customer_order WHERE order_id = :orderID FOR UPDATE")
                            .param("orderID", adminCheckPickingTask.path("orderID").asLong())
                            .query(Long.class).single();
                    adminReassignFlowTask(checkS6T11Admin, adminCheckSecondWorkerID)
                            .expect(409).code("ADMIN_PICKING_TASK_STATE_CONFLICT");
                });
        assertEquals(checkS6T11AssignmentsBefore, adminReadFlowAssignments());
        JsonNode checkS6T11Decision = adminReassignFlowTask(checkS6T11Admin, adminCheckSecondWorkerID)
                .expect(200).json();
        JsonNode checkS6T11Task = adminReadFlowPickingTask(checkS6T11Admin);
        assertEquals(adminCheckSecondWorkerID,
                checkS6T11Task.path("adminSearchPickingTaskCurrentWorkerUserID").asLong());
        adminCheckFlowDecidedRow(checkS6T11Decision, null,
                checkS6T11Task.path("adminSearchPickingTaskCurrentWorkerUsername").asText(),
                checkS6T11Task.path("adminSearchPickingTaskAssignedTime"), null);
        assertEquals(adminCheckPickingTask.path("adminSearchPickingTaskPickedQuantityTotal"),
                checkS6T11Task.path("adminSearchPickingTaskPickedQuantityTotal"));
        assertEquals(adminCheckPickingTask.path("adminSearchPickingTaskOrderedQuantityTotal"),
                checkS6T11Task.path("adminSearchPickingTaskOrderedQuantityTotal"));
        assertEquals(adminCheckPickingTask.path("taskStatus"), checkS6T11Task.path("taskStatus"));
        assertEquals(checkS6T11Stock, adminReadFlowStock());
        assertEquals(checkS6T11Scans, adminReadFlowScans(checkS6T11Admin, adminCheckPickingTask));

        // The two assignment rows transfer responsibility with no gap between their times.
        List<Map<String, Object>> checkS6T11Assignments = adminReadFlowAssignments();
        assertEquals(2, checkS6T11Assignments.size());
        Map<String, Object> checkS6T11Ended = checkS6T11Assignments.getFirst();
        Map<String, Object> checkS6T11Open = checkS6T11Assignments.getLast();
        assertEquals(checkS6T11FirstWorkerID, checkS6T11Ended.get("worker_user_id"));
        assertEquals(adminCheckSecondWorkerID, checkS6T11Open.get("worker_user_id"));
        assertNotNull(checkS6T11Ended.get("ended_at"));
        assertEquals(checkS6T11Open.get("assigned_at"), checkS6T11Ended.get("ended_at"));
        assertNull(checkS6T11Open.get("ended_at"));
        reportPartial("Selectable worker labels, current-worker exclusion and retained row are checked in S6-H07.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the two reassignment notifications.
    // What it does: Reads the newest notification rows for the transferred task.
    // Input: The reassignment completed in S6-T11.
    // Output: The old and new workers each receive the corresponding notice.
    @Test
    @DisplayName("S6-T12")
    void checkS6T12() {
        List<Map<String, Object>> checkS6T12Notifications = adminReadFlowNotifications();
        assertEquals(2, checkS6T12Notifications.size());
        for (Map<String, Object> checkS6T12Notification : checkS6T12Notifications) {
            String checkS6T12Type = checkS6T12Notification.get("type").toString();
            assertTrue(List.of("WORKER_PICKING_TASK_ASSIGNED", "WORKER_PICKING_TASK_REMOVED").contains(checkS6T12Type));
            long checkS6T12WorkerID = "WORKER_PICKING_TASK_ASSIGNED".equals(checkS6T12Type)
                    ? adminCheckSecondWorkerID : userID(adminCheckFirstWorkerUsername);
            adminCheckFlowNotification(checkS6T12Notification, adminCheckPickingTask,
                    checkS6T12WorkerID, checkS6T12Type);
        }
        assertNotEquals(checkS6T12Notifications.getFirst().get("type"), checkS6T12Notifications.getLast().get("type"));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the wrong-role and already-assigned worker rules.
    // What it does: Sends both invalid worker IDs and compares the assignment history.
    // Input: Original task and the second worker from S6-T11.
    // Output: Two refusals with identical before and after task records.
    @Test
    @DisplayName("S6-T13")
    void checkS6T13() {
        Session checkS6T13Admin = admin();
        long checkS6T13DeliveryWorkerID = userID(testUser(checkS6T13Admin, "DELIVERY_WORKER")[0]);
        List<Map<String, Object>> checkS6T13Assignments = adminReadFlowAssignments();
        adminReassignFlowTask(checkS6T13Admin, checkS6T13DeliveryWorkerID)
                .expect(409).code("ADMIN_WORKER_NOT_ALLOWED");
        adminReassignFlowTask(checkS6T13Admin, adminCheckSecondWorkerID)
                .expect(409).code("ADMIN_WORKER_NOT_ALLOWED");
        assertEquals(checkS6T13Assignments, adminReadFlowAssignments());
        assertEquals(adminCheckSecondWorkerID,
                adminReadFlowPickingTask(checkS6T13Admin).path("adminSearchPickingTaskCurrentWorkerUserID").asLong());
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks that the first worker has no active work after reassignment.
    // What it does: Changes the first worker's role through the Users API.
    // Input: The worker who lost this picking assignment.
    // Output: A successful switch to Delivery Worker.
    @Test
    @DisplayName("S6-T14")
    void checkS6T14() {
        Session checkS6T14Admin = admin();
        String checkS6T14WorkerAddress = USERS + "/" + userID(adminCheckFirstWorkerUsername);
        assertEquals(0, checkS6T14Admin.get(checkS6T14WorkerAddress + "/impact").expect(200).json()
                .path("adminPreviewUserImpactActivePickingTaskCount").asInt(-1));
        checkS6T14Admin.post(checkS6T14WorkerAddress + "/role", Map.of(
                "adminChangeUserRoleNewRole", "DELIVERY_WORKER",
                "adminChangeUserRoleReason", "Worker no longer holds picking work")).expect(200);
        assertEquals("DELIVERY_WORKER",
                checkS6T14Admin.get(checkS6T14WorkerAddress).expect(200).json().path("role").asText());
        reportPartial("The Users role-change controls are checked in S6-H08.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks recovery when a current worker is banned.
    // What it does: Bans the assigned worker, reads the task and reassigns it to an active worker.
    // Input: The task held by the second worker and a new active Warehouse Worker.
    // Output: A banned current worker flagged by the API, and an active replacement.
    @Test
    @DisplayName("S6-T15")
    void checkS6T15() {
        Session checkS6T15Admin = admin();
        long checkS6T15NewWorkerID = userID(testUser(checkS6T15Admin, "WAREHOUSE_WORKER")[0]);
        String checkS6T15WorkerAddress = USERS + "/" + adminCheckSecondWorkerID;
        assertEquals(1, checkS6T15Admin.get(checkS6T15WorkerAddress + "/impact").expect(200).json()
                .path("adminPreviewUserImpactActivePickingTaskCount").asInt(-1));
        checkS6T15Admin.post(checkS6T15WorkerAddress + "/ban",
                Map.of("adminBanUserReason", "Suspend this test account")).expect(200);
        assertEquals("BANNED",
                adminReadFlowPickingTask(checkS6T15Admin).path("adminSearchPickingTaskCurrentWorkerAccountStatus")
                        .asText());

        // The banned worker cannot be offered as an active replacement.
        JsonNode checkS6T15Workload = checkS6T15Admin.get(ADMIN_PICKING_TASKS + "/worker-workload").expect(200).json();
        assertFalse(adminReadFlowRows(checkS6T15Workload, ADMIN_WORKLOAD_ROWS).stream().anyMatch(
                checkS6T15Worker -> checkS6T15Worker.path("userID").asLong() == adminCheckSecondWorkerID));
        adminReassignFlowTask(checkS6T15Admin, checkS6T15NewWorkerID).expect(200);
        assertEquals(checkS6T15NewWorkerID,
                adminReadFlowPickingTask(checkS6T15Admin).path("adminSearchPickingTaskCurrentWorkerUserID").asLong());
        reportPartial("The ban window, row flag and replacement controls are checked in S6-H08.");
    }

    // ===== SECTION 4: AUDIT, CONCURRENCY, CSV AND ACCESS =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the saved decisions in the Audit log.
    // What it does: Reads the five audit actions recorded by earlier Warehouse cases.
    // Input: The wait, cancel, refund and reassignment fixtures.
    // Output: The required audit entries, including the stock correction assertion and reason.
    @Test
    @DisplayName("S6-T16")
    void checkS6T16() {
        Session checkS6T16Admin = admin();
        for (Map.Entry<String, JsonNode> checkS6T16Target : List.of(
                Map.entry("SHORTAGE_WAIT_DECIDED", adminCheckInitialShortage.path("shortageCaseID")),
                Map.entry("SHORTAGE_CANCEL_DECIDED", adminCheckPaidShortage.path("shortageCaseID")),
                Map.entry("ORDER_CANCELLED_BY_ADMIN", adminCheckPaidShortage.path("orderID")),
                Map.entry("REFUND_REQUESTED", adminCheckPaidShortage.path("orderID")),
                Map.entry("PICKING_TASK_REASSIGNED", adminCheckPickingTask.path("pickingTaskID")))) {
            String checkS6T16Action = checkS6T16Target.getKey();
            String checkS6T16Address = "/api/admin/logs/audit?adminSearchAuditLogAction=" + checkS6T16Action
                    + "&adminSearchAuditLogTargetID=" + checkS6T16Target.getValue().asLong();
            JsonNode checkS6T16Answer = checkS6T16Admin.get(checkS6T16Address).expect(200).json();
            List<JsonNode> checkS6T16Rows = adminReadFlowRows(checkS6T16Answer, "adminSearchAuditLogRowList");
            assertFalse(checkS6T16Rows.isEmpty(), checkS6T16Action);
            JsonNode checkS6T16Row = checkS6T16Rows.getFirst();
            assertEquals(checkS6T16Action, checkS6T16Row.path("action").asText());
            assertEquals(checkS6T16Target.getValue().asText(), checkS6T16Row.path("targetID").asText());
            if ("SHORTAGE_CANCEL_DECIDED".equals(checkS6T16Action)) {
                JsonNode checkS6T16Change = ADMIN_AUDIT_JSON.readTree(checkS6T16Row.path("newValue").asText());
                assertTrue(checkS6T16Change.path("stockCorrectionIsConfirmed").asBoolean());
                assertEquals(ADMIN_CANCEL_REASON, checkS6T16Change.path("reason").asText());
            }
        }
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks that two simultaneous cancel requests cannot duplicate the decision.
    // What it does: Sends cancellation requests from two independent sessions at once.
    // Input: A new paid picking shortage and two authenticated Admin sessions.
    // Output: One success, one conflict, one cancellation audit row and one refund.
    @Test
    @DisplayName("S6-T17")
    void checkS6T17() throws Exception {
        Session checkS6T17First = admin();
        Session checkS6T17Second = admin();
        JsonNode checkS6T17Shortage = adminCreateFlowShortage(checkS6T17First, "PICKING");
        String checkS6T17AuditQuery = "SELECT count(*) FROM audit_log WHERE action = 'ORDER_CANCELLED_BY_ADMIN'";
        long checkS6T17CountBefore = count(checkS6T17AuditQuery);

        // Wait at a shared barrier after each session is ready to send its request.
        CyclicBarrier checkS6T17Barrier = new CyclicBarrier(2);
        try (var checkS6T17Executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var checkS6T17Results = new ArrayList<Future<Reply>>();
            for (Session checkS6T17Session : List.of(checkS6T17First, checkS6T17Second)) {
                JsonNode checkS6T17Token = checkS6T17Session.get(SESSION).expect(200).json();
                checkS6T17Results.add(checkS6T17Executor.submit(() -> {
                    checkS6T17Barrier.await(10, TimeUnit.SECONDS);
                    return checkS6T17Session.send("POST", adminReadFlowCancelAddress(checkS6T17Shortage),
                            adminBuildFlowCancelRequest(true), checkS6T17Token);
                }));
            }
            Reply checkS6T17ResultOne = checkS6T17Results.getFirst().get(35, TimeUnit.SECONDS);
            Reply checkS6T17ResultTwo = checkS6T17Results.getLast().get(35, TimeUnit.SECONDS);
            Reply checkS6T17Success = checkS6T17ResultOne.response().statusCode() == 200
                    ? checkS6T17ResultOne : checkS6T17ResultTwo;
            Reply checkS6T17Conflict = checkS6T17Success == checkS6T17ResultOne
                    ? checkS6T17ResultTwo : checkS6T17ResultOne;
            checkS6T17Success.expect(200);
            checkS6T17Conflict.expect(409).code("ADMIN_SHORTAGE_CASE_STATE_CONFLICT");
        }
        assertEquals(checkS6T17CountBefore + 1, count(checkS6T17AuditQuery));
        assertEquals(1, adminCountFlowRefunds(checkS6T17Shortage.path("orderID").asLong()));
        reportPartial("The browser double-click, single result message and absence of a browser error are S6-H09.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the filtered shortage download and its audit.
    // What it does: Downloads resolved cases and checks the filename, rows and audit entry.
    // Input: The resolved case from S6-T03 and the resolved filter.
    // Output: A UTF-8 CSV file containing the selected cases and a recorded download.
    @Test
    @DisplayName("S6-T18")
    void checkS6T18() {
        Session checkS6T18Admin = admin();
        Reply checkS6T18File = checkS6T18Admin.get(
                ADMIN_SHORTAGE_CASES + "/csv?" + ADMIN_SHORTAGE_CASE_FILTER + "=RESOLVED").expect(200);
        assertTrue(checkS6T18File.header("Content-Disposition")
                .matches("attachment;.*admin-shortage-cases-[0-9]{8}-[0-9]{6}\\.csv.*"));
        assertTrue(checkS6T18File.header("Content-Type").startsWith("text/csv"));
        assertTrue(checkS6T18File.body().startsWith("\uFEFF"));
        assertTrue(checkS6T18File.body().contains("\"shortageCaseID\""));
        String checkS6T18ResolvedReference = adminReadFlowShortageReference(adminCheckInitialShortage);
        String checkS6T18OtherReference = adminReadFlowShortageReference(adminCheckPaidShortage);
        assertTrue(checkS6T18File.body().contains("\"" + checkS6T18ResolvedReference + "\""));
        assertFalse(checkS6T18File.body().contains("\"" + checkS6T18OtherReference + "\""));
        assertTrue(count("""
                SELECT count(*) FROM audit_log WHERE action = 'CSV_DOWNLOADED' AND target_id = 'CSV_SHORTAGE_CASES'
                """) > 0);
        reportPartial("The browser download control and saved file are checked in S6-H04.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks that other roles cannot view the shortage queue.
    // What it does: Signs in separately as the earlier non-Admin test account.
    // Input: The mei_picker credentials from the earlier stage checks.
    // Output: The shared role guard refuses the shortage API.
    @Test
    @DisplayName("S6-T19")
    void checkS6T19() {
        Session checkS6T19Worker = browser();
        checkS6T19Worker.login("mei_picker", FLOW_PICKER_PASSWORD).expect(200);
        checkS6T19Worker.get(ADMIN_SHORTAGE_CASES).expect(403).code("SHARED_ACCESS_DENIED");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the no-refund path even when a zero-value payment was accepted.
    // What it does: Sets the accepted fixture payment to zero and cancels the shortage.
    // Input: A paid picking order from the sample builder.
    // Output: One final cancellation, no refund and the unpaid client notification.
    @Test
    @DisplayName("S6-T21")
    void checkS6T21() {
        Session checkS6T21Admin = admin();
        JsonNode checkS6T21Shortage = adminCreateFlowShortage(checkS6T21Admin, "PICKING");
        long orderID = checkS6T21Shortage.path("orderID").asLong();
        assertTrue(checkS6T21Shortage.path("adminSearchShortageCaseOrderIsPaid").asBoolean());
        assertEquals(1, database.sql("UPDATE payment SET amount = 0 WHERE order_id = :orderID")
                .param("orderID", orderID).update());

        // A zero amount counts as unpaid even though the payment retains its accepted status.
        JsonNode checkS6T21Open = adminReadFlowShortage(checkS6T21Admin, "OPEN_AND_WAITING",
                checkS6T21Shortage.path("shortageCaseID"));
        assertFalse(checkS6T21Open.path("adminSearchShortageCaseOrderIsPaid").asBoolean(true));
        JsonNode checkS6T21Decision = adminCancelFlowShortage(checkS6T21Admin, checkS6T21Open, true)
                .expect(200).json();
        assertEquals("Order " + adminReadFlowShortageReference(checkS6T21Open)
                + " was cancelled. Nothing had been paid, so no refund was needed.",
                checkS6T21Decision.path("message").asText());
        JsonNode checkS6T21Case = adminReadFlowShortage(checkS6T21Admin, "CANCELLED",
                checkS6T21Open.path("shortageCaseID"));
        adminCheckFlowDecidedRow(checkS6T21Decision, "CANCELLED", null, checkS6T21Case.path("decidedTime"), null);
        JsonNode checkS6T21Order = adminReadFlowOrder(checkS6T21Admin, checkS6T21Open);
        assertEquals("CANCELLED", checkS6T21Order.path("orderStatus").asText());
        JsonNode checkS6T21Payment = adminReadFlowRows(checkS6T21Order, "adminViewOrderPaymentList").getFirst();
        assertEquals("ACCEPTED", checkS6T21Payment.path("paymentStatus").asText());
        assertEquals(0, checkS6T21Payment.path("amount").decimalValue().signum());
        assertEquals(0, adminCountFlowRefunds(orderID));
        adminCheckFlowNotification(adminReadFlowNotifications().getFirst(), checkS6T21Open,
                checkS6T21Order.path("clientUserID").asLong(), "CLIENT_SHORTAGE_CANCELLED_UNPAID");
        reportPartial("The browser rendering of the zero-payment row and cancellation window is omitted.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 7 repeats the shortage cancellation with both a positive and a newer zero-value payment.
    // What it does: Checks that the paid preview and refund use the positive payment.
    // Input: A picking shortage with an accepted payment, plus a newer zero-value payment.
    // Output: A new full refund associated with the older positive payment only.
    @Test
    @DisplayName("S6-T22")
    void checkS6T22() {
        Session checkS6T22Admin = admin();
        JsonNode checkS6T22Shortage = adminCreateFlowShortage(checkS6T22Admin, "PICKING");
        long orderID = checkS6T22Shortage.path("orderID").asLong();
        long paymentID = database.sql("SELECT payment_id FROM payment WHERE order_id = :orderID")
                .param("orderID", orderID).query(Long.class).single();
        assertEquals(1, database.sql("""
                INSERT INTO payment (order_id, amount, payment_status, accepted_at)
                VALUES (:orderID, 0, 'ACCEPTED', now())
                """).param("orderID", orderID).update());

        JsonNode checkS6T22OpenCase = adminReadFlowShortage(checkS6T22Admin, "OPEN_AND_WAITING",
                checkS6T22Shortage.path("shortageCaseID"));
        assertTrue(checkS6T22OpenCase.path("adminSearchShortageCaseOrderIsPaid").asBoolean());
        JsonNode checkS6T22Decision = adminCancelFlowShortage(checkS6T22Admin, checkS6T22OpenCase, true)
                .expect(200).json();
        assertEquals("Order " + adminReadFlowShortageReference(checkS6T22OpenCase)
                + " was cancelled. A full refund was requested.", checkS6T22Decision.path("message").asText());
        assertTrue(checkS6T22Decision.path("adminDecidedRowRefundIsNew").asBoolean());

        List<Map<String, Object>> checkS6T22RefundList = database.sql("""
                SELECT refund.refund_id, refund.payment_id, refund.amount, payment.amount AS paid_amount
                FROM refund JOIN payment USING (payment_id) WHERE payment.order_id = :orderID
                """).param("orderID", orderID).query().listOfRows();
        assertEquals(1, checkS6T22RefundList.size());
        Map<String, Object> checkS6T22Refund = checkS6T22RefundList.getFirst();
        assertEquals(paymentID, checkS6T22Refund.get("payment_id"));
        assertEquals(checkS6T22Refund.get("paid_amount"), checkS6T22Refund.get("amount"));
        assertEquals(checkS6T22Refund.get("refund_id"), checkS6T22Decision.path("refundID").asLong());
        reportPartial("The browser rendering of the paid cancellation window is omitted.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 7 checks that shortage cancellation recognizes a completed refund.
    // What it does: Cancels a shortage with a pre-existing completed refund and checks the saved result.
    // Input: A picking shortage with one accepted payment and one REFUNDED refund.
    // Output: A final cancellation that reuses the refund ID, records the result and notifies the client.
    @Test
    @DisplayName("S6-T23")
    void checkS6T23() {
        Session checkS6T23Admin = admin();
        JsonNode checkS6T23Shortage = adminCreateFlowShortage(checkS6T23Admin, "PICKING");
        long orderID = checkS6T23Shortage.path("orderID").asLong();
        long shortageCaseID = checkS6T23Shortage.path("shortageCaseID").asLong();
        long refundID = database.sql("""
                INSERT INTO refund (payment_id, amount, refund_status, completed_at)
                SELECT payment_id, amount, 'REFUNDED', now()
                FROM payment WHERE order_id = :orderID AND accepted_at IS NOT NULL
                RETURNING refund_id
                """).param("orderID", orderID).query(Long.class).single();

        JsonNode checkS6T23Decision = adminCancelFlowShortage(checkS6T23Admin, checkS6T23Shortage, true)
                .expect(200).json();
        assertEquals("Order " + adminReadFlowShortageReference(checkS6T23Shortage)
                + " was cancelled. Refund " + refundID
                + " of this payment already existed, so no new refund was requested.",
                checkS6T23Decision.path("message").asText());
        assertEquals(refundID, checkS6T23Decision.path("refundID").asLong());
        assertFalse(checkS6T23Decision.path("adminDecidedRowRefundIsNew").asBoolean(true));
        assertEquals(1, adminCountFlowRefunds(orderID));
        assertEquals(0L, database.sql("""
                SELECT count(*) FROM audit_log WHERE action = 'REFUND_REQUESTED' AND target_id = :targetID
                """).param("targetID", Long.toString(orderID)).query(Long.class).single());
        assertEquals(Long.toString(refundID), database.sql("""
                SELECT new_value ->> 'existingRefundID' FROM audit_log
                WHERE action = 'SHORTAGE_CANCEL_DECIDED' AND target_id = :targetID
                """).param("targetID", Long.toString(shortageCaseID)).query(String.class).single());

        JsonNode checkS6T23Order = adminReadFlowOrder(checkS6T23Admin, checkS6T23Shortage);
        assertEquals("CANCELLED", checkS6T23Order.path("orderStatus").asText());
        Map<String, Object> checkS6T23Notification = adminReadFlowNotifications().getFirst();
        adminCheckFlowNotification(checkS6T23Notification, checkS6T23Shortage,
                checkS6T23Order.path("clientUserID").asLong(), "CLIENT_SHORTAGE_CANCELLED_REFUND_EXISTS");
        assertTrue(checkS6T23Notification.get("message").toString()
                .contains("A refund for this order had already been started."));
        reportPartial("The rendered Warehouse row's existing-refund line is omitted.");
    }

    // ===== SECTION 5: FLOW HELPERS =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks the shared result that redraws a work row after a successful action.
    // What it does: Compares the decision reply with the state and time saved by the action.
    // Input: Decision, expected state and worker, saved time, and expected refund ID.
    // Output: Assertions on all six contract fields.
    private void adminCheckFlowDecidedRow(JsonNode adminCheckFlowDecidedRowDecision, String adminDecidedRowStateWord,
            String adminDecidedRowWorkerUsername, JsonNode adminDecidedRowDecidedTime, Long refundID) {
        assertFalse(adminCheckFlowDecidedRowDecision.path("message").asText().isBlank());
        assertEquals(bootstrapUsername,
                adminCheckFlowDecidedRowDecision.path("adminDecidedRowDecidedByUsername").asText());
        assertEquals(Instant.parse(adminDecidedRowDecidedTime.asText()),
                Instant.parse(adminCheckFlowDecidedRowDecision.path("adminDecidedRowDecidedTime").asText()));
        assertEquals(adminDecidedRowStateWord,
                adminCheckFlowDecidedRowDecision.path("adminDecidedRowStateWord").asText(null));
        assertEquals(adminDecidedRowWorkerUsername,
                adminCheckFlowDecidedRowDecision.path("adminDecidedRowWorkerUsername").asText(null));
        if (refundID == null) {
            assertTrue(adminCheckFlowDecidedRowDecision.path("refundID").isNull());
        } else {
            assertEquals(refundID.longValue(), adminCheckFlowDecidedRowDecision.path("refundID").asLong());
        }
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 creates each shortage with the same test tool used by the browser.
    // What it does: Creates a shortage for a new synthetic order.
    // Input: Admin session and requested order status.
    // Output: The case DTO, found with the returned case number.
    private JsonNode adminCreateFlowShortage(Session adminCreateFlowShortageAdmin, String orderStatus) {
        return adminReadCreatedFlowShortage(adminCreateFlowShortageAdmin,
                adminCreateFlowShortageAdmin.tool("shortage-report", Map.of(
                        "adminTemporaryCreateShortageReportOrderStatus", orderStatus,
                        "adminTemporaryCreateShortageReportMissingQuantity", 1)).expect(200));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 matches each tool response to the corresponding case.
    // What it does: Looks up the reported case and verifies its order reference.
    // Input: Admin session and successful shortage tool reply.
    // Output: The newly reported open case.
    private JsonNode adminReadCreatedFlowShortage(Session adminReadCreatedFlowShortageAdmin,
            Reply adminReadCreatedFlowShortageReply) {
        var adminReadCreatedFlowShortageMatch = ADMIN_SHORTAGE_TOOL_MESSAGE
                .matcher(adminReadCreatedFlowShortageReply.json().path("message").asText());
        assertTrue(adminReadCreatedFlowShortageMatch.matches(), "The tool must return the case and order numbers.");
        String shortageCaseID = adminReadCreatedFlowShortageMatch.group(1);
        JsonNode adminReadCreatedFlowShortageRow = adminFindFlowRow(
                adminReadFlowShortages(adminReadCreatedFlowShortageAdmin, "OPEN_AND_WAITING"),
                "shortageCaseID", shortageCaseID);
        assertEquals(adminReadCreatedFlowShortageMatch.group(2),
                adminReadFlowShortageReference(adminReadCreatedFlowShortageRow));
        return adminReadCreatedFlowShortageRow;
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 reads the queue through the same filter and pager used by the page.
    // What it does: Reads all of the requested shortage pages.
    // Input: Admin session and shortage filter.
    // Output: Matching rows from all pages, in server order.
    private List<JsonNode> adminReadFlowShortages(Session adminReadFlowShortagesAdmin, String caseStatus) {
        return adminReadFlowPages(adminReadFlowShortagesAdmin,
                ADMIN_SHORTAGE_CASES + "?" + ADMIN_SHORTAGE_CASE_FILTER + "=" + caseStatus,
                "adminSearchShortageCase", ADMIN_SHORTAGE_CASE_ROWS);
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 selects a case by ID even when fixture rows span several pages.
    // What it does: Finds the shortage case in a specific filtered queue.
    // Input: Admin session, shortage filter and case ID.
    // Output: One matching case.
    private JsonNode adminReadFlowShortage(
            Session adminReadFlowShortageAdmin, String caseStatus, JsonNode shortageCaseID) {
        return adminFindFlowRow(adminReadFlowShortages(adminReadFlowShortageAdmin, caseStatus),
                "shortageCaseID", shortageCaseID.asText());
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 reads the picking task API regardless of the existing number of assignments.
    // What it does: Reads all pages of the picking queue.
    // Input: Admin session.
    // Output: The full picking queue.
    private List<JsonNode> adminReadFlowPickingTasks(Session adminReadFlowPickingTasksAdmin) {
        return adminReadFlowPages(adminReadFlowPickingTasksAdmin, ADMIN_PICKING_TASKS,
                "adminSearchPickingTask", ADMIN_PICKING_TASK_ROWS);
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 identifies the same task before and after reassignment.
    // What it does: Finds the picking task created by S6-T10.
    // Input: Admin session and the saved fixture task ID.
    // Output: Current task data.
    private JsonNode adminReadFlowPickingTask(Session adminReadFlowPickingTaskAdmin) {
        return adminFindFlowRow(adminReadFlowPickingTasks(adminReadFlowPickingTaskAdmin),
                "pickingTaskID", adminCheckPickingTask.path("pickingTaskID").asText());
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 follows pagination so earlier-stage fixtures cannot hide a new case or task.
    // What it does: Reads pages of a list API using its documented page parameters.
    // Input: Admin session, list address, function name and row-list field.
    // Output: All matching rows in page order.
    private List<JsonNode> adminReadFlowPages(Session adminReadFlowPagesAdmin, String adminReadFlowPagesWebAddress,
            String adminReadFlowPagesFunction, String adminReadFlowPagesRowField) {
        var adminReadFlowPagesRowList = new ArrayList<JsonNode>();
        int adminReadFlowPagesTotalPageCount = 1;
        String adminReadFlowPagesSeparator = adminReadFlowPagesWebAddress.contains("?") ? "&" : "?";
        for (int adminReadFlowPagesPageNumber = 1; adminReadFlowPagesPageNumber <= adminReadFlowPagesTotalPageCount;
                adminReadFlowPagesPageNumber++) {
            JsonNode adminReadFlowPagesAnswer = adminReadFlowPagesAdmin.get(adminReadFlowPagesWebAddress
                    + adminReadFlowPagesSeparator + adminReadFlowPagesFunction
                    + "PageNumber=" + adminReadFlowPagesPageNumber).expect(200).json();
            assertEquals(adminReadFlowPagesPageNumber,
                    adminReadFlowPagesAnswer.path(adminReadFlowPagesFunction + "PageNumber").asInt());
            adminReadFlowPagesTotalPageCount = adminReadFlowPagesAnswer
                    .path(adminReadFlowPagesFunction + "TotalPageCount").asInt();
            assertTrue(adminReadFlowPagesTotalPageCount >= 1);
            adminReadFlowPagesRowList.addAll(adminReadFlowRows(adminReadFlowPagesAnswer, adminReadFlowPagesRowField));
        }
        return adminReadFlowPagesRowList;
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks that the requested API collection exists before reading its rows.
    // What it does: Reads an array field in a decoded answer.
    // Input: The JSON answer and its row-list field name.
    // Output: A list in API order.
    private List<JsonNode> adminReadFlowRows(JsonNode adminReadFlowRowsAnswer, String adminReadFlowRowsField) {
        JsonNode adminReadFlowRowsList = adminReadFlowRowsAnswer.path(adminReadFlowRowsField);
        assertTrue(adminReadFlowRowsList.isArray(), adminReadFlowRowsField);
        return StreamSupport.stream(adminReadFlowRowsList.spliterator(), false).toList();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 finds rows without depending on their position among earlier-stage fixtures.
    // What it does: Finds a row with the supplied field value.
    // Input: Candidate rows, matching field and expected value.
    // Output: The matching row or a test failure.
    private JsonNode adminFindFlowRow(List<JsonNode> adminFindFlowRowList, String adminFindFlowRowField,
            String adminFindFlowRowValue) {
        return adminFindFlowRowList.stream().filter(adminFindFlowRowCandidate ->
                adminFindFlowRowValue.equals(adminFindFlowRowCandidate.path(adminFindFlowRowField).asText()))
                .findFirst().orElseThrow(() -> new AssertionError("No row for " + adminFindFlowRowField
                        + " " + adminFindFlowRowValue));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 uses the existing order API to check final order, payment and refund states.
    // What it does: Reads the order linked by the shortage or picking queue.
    // Input: Admin session and a row that carries the order ID.
    // Output: The order detail reply.
    private JsonNode adminReadFlowOrder(Session adminReadFlowOrderAdmin, JsonNode adminReadFlowOrderSource) {
        return adminReadFlowOrderAdmin.get("/api/admin/orders/" + adminReadFlowOrderSource.path("orderID").asLong())
                .expect(200).json();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 uses the shortage row's public order reference in its assertions.
    // What it does: Reads the order reference from the shortage queue DTO.
    // Input: A shortage case row.
    // Output: Its order reference.
    private String adminReadFlowShortageReference(JsonNode adminReadFlowShortageReferenceRow) {
        return adminReadFlowShortageReferenceRow.path("adminSearchShortageCaseOrderReference").asText();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 sends the same wait request as the browser form.
    // What it does: Asks the server to wait for restock.
    // Input: Admin session and the shortage case row.
    // Output: The decision response.
    private Reply adminWaitForFlowShortage(
            Session adminWaitForFlowShortageAdmin, JsonNode adminWaitForFlowShortageRow) {
        return adminWaitForFlowShortageAdmin.post(
                ADMIN_SHORTAGE_CASES + "/" + adminWaitForFlowShortageRow.path("shortageCaseID").asLong() + "/wait",
                Map.of("adminDecideShortageWaitReason", ADMIN_WAIT_REASON));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 sends the browser form's cancel request through two sessions for the concurrency check.
    // What it does: Builds the case-specific cancel URL.
    // Input: The shortage case row.
    // Output: The cancel endpoint.
    private String adminReadFlowCancelAddress(JsonNode adminReadFlowCancelAddressRow) {
        return ADMIN_SHORTAGE_CASES + "/" + adminReadFlowCancelAddressRow.path("shortageCaseID").asLong() + "/cancel";
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks confirmed and unconfirmed sends with the same request shape.
    // What it does: Builds the cancellation payload.
    // Input: Whether the stock correction was confirmed.
    // Output: The exact reason and confirmation fields sent to the API.
    private Map<String, Object> adminBuildFlowCancelRequest(boolean adminBuildFlowCancelRequestIsConfirmed) {
        return Map.of("adminDecideShortageCancelReason", ADMIN_CANCEL_REASON,
                "adminDecideShortageCancelStockCorrectionIsConfirmed", adminBuildFlowCancelRequestIsConfirmed);
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 applies cancellation through the live route.
    // What it does: Submits a cancellation action for one shortage.
    // Input: Admin session, shortage case row and whether the stock correction was confirmed.
    // Output: The decision response.
    private Reply adminCancelFlowShortage(Session adminCancelFlowShortageAdmin, JsonNode adminCancelFlowShortageRow,
            boolean adminCancelFlowShortageIsConfirmed) {
        return adminCancelFlowShortageAdmin.post(adminReadFlowCancelAddress(adminCancelFlowShortageRow),
                adminBuildFlowCancelRequest(adminCancelFlowShortageIsConfirmed));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 reuses the same picking fixture across worker reassignment cases.
    // What it does: Sends a reassignment to the live route.
    // Input: Admin session and new worker ID.
    // Output: The decision response.
    private Reply adminReassignFlowTask(Session adminReassignFlowTaskAdmin, long adminReassignFlowTaskNewWorkerID) {
        return adminReassignFlowTaskAdmin.post(
                ADMIN_PICKING_TASKS + "/" + adminCheckPickingTask.path("pickingTaskID").asLong() + "/reassign",
                Map.of("adminReassignPickingTaskNewWorkerUserID", adminReassignFlowTaskNewWorkerID,
                        "adminReassignPickingTaskReason", ADMIN_REASSIGN_REASON));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 confirms reassignment does not rewrite the scan history read by Logs.
    // What it does: Filters the log to the fixture order.
    // Input: Admin session and picking row.
    // Output: All scan rows for the fixture order reference.
    private List<JsonNode> adminReadFlowScans(Session adminReadFlowScansAdmin, JsonNode adminReadFlowScansTask) {
        return adminReadFlowPages(adminReadFlowScansAdmin,
                "/api/admin/logs/picking-scan?adminSearchPickingScanHistoryOrderReference="
                        + adminReadFlowScansTask.path("adminSearchPickingTaskOrderReference").asText(),
                "adminSearchPickingScanHistory", "adminSearchPickingScanHistoryRowList");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 compares the assignment rows directly with the plan's verification query.
    // What it does: Reads the fixture task's worker assignment history.
    // Input: The saved fixture task ID.
    // Output: Rows in assignment ID order.
    private List<Map<String, Object>> adminReadFlowAssignments() {
        return database.sql("""
                SELECT worker_user_id, assigned_at, ended_at FROM picking_assignment_history
                WHERE picking_task_id = :pickingTaskID ORDER BY assignment_id
                """).param("pickingTaskID", adminCheckPickingTask.path("pickingTaskID").asLong()).query().listOfRows();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks that only reservation balances change during cancellation.
    // What it does: Reads physical and reserved stock for synthetic products.
    // Input: The isolated flow database.
    // Output: Stock rows in SKU order.
    private List<Map<String, Object>> adminReadFlowStock() {
        return database.sql("""
                SELECT p.sku, i.physical_stock, i.reserved_stock
                FROM product p JOIN inventory i USING (product_id)
                WHERE p.sku LIKE 'TEST-%' ORDER BY p.sku
                """).query().listOfRows();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 verifies the stock deltas without assuming fixture SKU numbers or starting balances.
    // What it does: Calculates the expected inventory by subtracting just the cancelled reservations.
    // Input: Cancelled order ID and the inventory snapshot before cancellation.
    // Output: A comparison against the complete post-cancellation query result.
    private void adminCheckFlowStockRelease(long orderID, List<Map<String, Object>> adminCheckFlowStockReleaseBefore) {
        List<Map<String, Object>> adminCheckFlowStockReleaseItems = database.sql("""
                SELECT product.sku, item.quantity FROM order_item item
                JOIN product USING (product_id) WHERE item.order_id = :orderID
                """).param("orderID", orderID).query().listOfRows();
        assertEquals(List.of(1, 3), adminCheckFlowStockReleaseItems.stream()
                .map(adminCheckFlowStockReleaseItem -> (Integer) adminCheckFlowStockReleaseItem.get("quantity"))
                .sorted().toList());
        var adminCheckFlowStockReleaseQuantityBySKU = new HashMap<String, Integer>();
        for (Map<String, Object> adminCheckFlowStockReleaseItem : adminCheckFlowStockReleaseItems) {
            adminCheckFlowStockReleaseQuantityBySKU.put(
                    (String) adminCheckFlowStockReleaseItem.get("sku"),
                    (Integer) adminCheckFlowStockReleaseItem.get("quantity"));
        }
        var adminCheckFlowStockReleaseExpected = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> adminCheckFlowStockReleaseRow : adminCheckFlowStockReleaseBefore) {
            var adminCheckFlowStockReleaseExpectedRow = new HashMap<>(adminCheckFlowStockReleaseRow);
            int adminCheckFlowStockReleaseQuantity = adminCheckFlowStockReleaseQuantityBySKU
                    .getOrDefault(adminCheckFlowStockReleaseRow.get("sku"), 0);
            adminCheckFlowStockReleaseExpectedRow.put("reserved_stock",
                    (Integer) adminCheckFlowStockReleaseRow.get("reserved_stock") - adminCheckFlowStockReleaseQuantity);
            adminCheckFlowStockReleaseExpected.add(adminCheckFlowStockReleaseExpectedRow);
        }
        assertEquals(adminCheckFlowStockReleaseExpected, adminReadFlowStock());
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks delivery of client and worker notifications to the right accounts.
    // What it does: Reads the plan's newest-notifications query with the linked order ID.
    // Input: The isolated flow database.
    // Output: At most two notification rows, newest first.
    private List<Map<String, Object>> adminReadFlowNotifications() {
        return database.sql("""
                SELECT recipient_user_id, order_id, type, message
                FROM notification ORDER BY notification_id DESC LIMIT 2
                """).query().listOfRows();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 checks that every sampled notification contains only public order information.
    // What it does: Checks the recipient, type, order and absence of seeded private markers.
    // Input: Notification row, source row, expected recipient ID and notification type.
    // Output: Assertions for the saved notification.
    private void adminCheckFlowNotification(Map<String, Object> adminCheckFlowNotificationRow,
            JsonNode adminCheckFlowNotificationSource, long recipientUserID, String type) {
        assertEquals(recipientUserID, adminCheckFlowNotificationRow.get("recipient_user_id"));
        assertEquals(adminCheckFlowNotificationSource.path("orderID").asLong(),
                adminCheckFlowNotificationRow.get("order_id"));
        assertEquals(type, adminCheckFlowNotificationRow.get("type"));
        String message = adminCheckFlowNotificationRow.get("message").toString();
        String orderReference = adminCheckFlowNotificationSource.has("adminSearchShortageCaseOrderReference")
                ? adminReadFlowShortageReference(adminCheckFlowNotificationSource)
                : adminCheckFlowNotificationSource.path("adminSearchPickingTaskOrderReference").asText();
        assertTrue(message.contains(orderReference));
        assertFalse(message.contains("PRIVATE-"));
        assertFalse(message.contains("5550 0000 1234"));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 6 counts refunds against one order so other fixture orders cannot affect the result.
    // What it does: Counts the refund rows associated with an order's payments.
    // Input: The order ID.
    // Output: The refund row count.
    private long adminCountFlowRefunds(long orderID) {
        return database.sql("""
                SELECT count(*) FROM refund JOIN payment USING (payment_id) WHERE payment.order_id = :orderID
                """).param("orderID", orderID).query(Long.class).single();
    }
}
