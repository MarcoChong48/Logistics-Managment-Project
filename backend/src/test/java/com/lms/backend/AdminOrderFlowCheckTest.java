// TEMPORARY TEST CODE - remove before integration
// Stage 5 checks the Orders pages' APIs using synthetic orders in the isolated flow database.
package com.lms.backend;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_ATTEMPT_OUTCOME_DELIVERED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_ATTEMPT_OUTCOME_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_FAILURE_REASON_CLIENT_NOT_HOME;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_ACCEPTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_REQUESTED;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import com.lms.backend.shared.value.SharedOrderStatus;
import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AdminOrderFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: ADDRESSES AND FIXTURES =====

    private static final String ORDERS = "/api/admin/orders";
    private static final String ORDER_LIST_PAGE = "/admin/admin-orders.html";
    private static final String ORDER_DETAIL_PAGE = "/admin/admin-order-detail.html";
    private static final String SCANS = "/api/admin/logs/picking-scan";
    private static final String PAYMENT_STATUS_FIELD = "adminSearchOrderLatestPaymentStatus";
    private static final String REFUND_STATUS_FIELD = "adminSearchOrderLatestRefundStatus";
    private static final String UNKNOWN_PAYMENT_STATUS = "SOMETHING_NEW";
    private static final Pattern SAMPLE_ORDER_REFERENCE = Pattern.compile("TEST-[0-9]+");
    private static final List<String> ORDER_CSV_COLUMN_LIST = List.of(
            "orderID", "orderReference", "clientUserID", "adminSearchOrderClientUsername", "orderStatus",
            "createdTime", "paymentDeadline", "totalAmount", PAYMENT_STATUS_FIELD, REFUND_STATUS_FIELD,
            "adminSearchOrderHasOpenCase");

    private List<JsonNode> adminCheckSampleOrderRowList;

    // ===== SECTION 2: ORDER SEARCH AND DETAIL CASES =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 uses the sample order set and checks the first search result.
    // What it does: Creates the ten fixture orders and finds them in newest-first API results.
    // Input: A signed-in Admin and any orders created by earlier flow cases.
    // Output: Ten fixture rows with separate order, payment and refund fields.
    @Test
    @DisplayName("S5-T01")
    void checkS5T01() {
        Session checkS5T01Admin = admin();
        String checkS5T01Message = checkS5T01Admin.tool("sample-order-set", null).expect(200)
                .json().path("message").asText();
        Set<String> checkS5T01ReferenceSet = new HashSet<>();
        SAMPLE_ORDER_REFERENCE.matcher(checkS5T01Message).results()
                .forEach(checkS5T01Match -> assertTrue(checkS5T01ReferenceSet.add(checkS5T01Match.group())));
        assertEquals(10, checkS5T01ReferenceSet.size());

        // Earlier stages leave rows behind, so find the ten referenced orders without assuming an empty list.
        List<JsonNode> checkS5T01RowList = adminReadOrderPages(checkS5T01Admin, "");
        adminCheckOrderSearchOrder(checkS5T01RowList);
        adminCheckSampleOrderRowList = checkS5T01RowList.stream()
                .filter(checkS5T01Row -> checkS5T01ReferenceSet.contains(checkS5T01Row.path("orderReference").asText()))
                .toList();
        assertEquals(10, adminCheckSampleOrderRowList.size());
        for (SharedOrderStatus orderStatus : SharedOrderStatus.values()) {
            JsonNode checkS5T01Order = adminFindSampleOrder(orderStatus);
            assertTrue(checkS5T01Order.has(PAYMENT_STATUS_FIELD));
            assertTrue(checkS5T01Order.has(REFUND_STATUS_FIELD));
            String checkS5T01PlainStatus = orderStatus.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            assertTrue(checkS5T01Message.toLowerCase(Locale.ROOT).contains(
                    checkS5T01Order.path("orderReference").asText().toLowerCase(Locale.ROOT)
                    + " (" + checkS5T01PlainStatus + ")"));
        }
        checkS5T01Admin.get(ORDER_LIST_PAGE).expect(200);
        reportPartial("The list appearance and separate columns are checked by hand in S5-H02.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks each filter through the same API the search page uses.
    // What it does: Compares separate search filters with the complete set of orders and verifies clearing.
    // Input: The fixture picking row and all orders already created.
    // Output: Matching rows for status, partial reference, client and created time.
    @Test
    @DisplayName("S5-T02")
    void checkS5T02() {
        Session checkS5T02Admin = admin();
        List<JsonNode> checkS5T02Unfiltered = adminReadOrderPages(checkS5T02Admin, "");
        JsonNode checkS5T02PickingRow = adminFindSampleOrder(SharedOrderStatus.PICKING);
        String orderReference = checkS5T02PickingRow.path("orderReference").asText();
        String checkS5T02PartialReference = orderReference.substring(0, orderReference.length() - 1);
        String checkS5T02Status = SharedOrderStatus.PICKING.name();
        String checkS5T02StatusFilter = "adminSearchOrderOrderStatus=" + checkS5T02Status;
        Instant checkS5T02FromTime = Instant.parse(checkS5T02PickingRow.path("createdTime").asText())
                .truncatedTo(ChronoUnit.DAYS);
        Instant checkS5T02ToTime = checkS5T02FromTime.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.MICROS);

        // Compare each result against the expected ordered subset, including rows from earlier stages.
        adminCheckOrderFilter(checkS5T02Admin, checkS5T02Unfiltered, checkS5T02StatusFilter,
                checkS5T02Row -> checkS5T02Status.equals(checkS5T02Row.path("orderStatus").asText()));
        adminCheckOrderFilter(checkS5T02Admin, checkS5T02Unfiltered,
                "adminSearchOrderKeyword=" + checkS5T02PartialReference.toLowerCase(Locale.ROOT),
                checkS5T02Row -> checkS5T02Row.path("orderReference").asText().contains(checkS5T02PartialReference));
        adminCheckOrderFilter(checkS5T02Admin, checkS5T02Unfiltered, "adminSearchOrderKeyword=test_client",
                checkS5T02Row -> checkS5T02Row.path("adminSearchOrderClientUsername").asText()
                        .toLowerCase(Locale.ROOT).contains("test_client"));
        adminCheckOrderFilter(checkS5T02Admin, checkS5T02Unfiltered,
                "adminSearchOrderCreatedFromTime=" + checkS5T02FromTime
                        + "&adminSearchOrderCreatedToTime=" + checkS5T02ToTime,
                checkS5T02Row -> {
                    Instant createdTime = Instant.parse(checkS5T02Row.path("createdTime").asText());
                    return !createdTime.isBefore(checkS5T02FromTime) && !createdTime.isAfter(checkS5T02ToTime);
                });
        reportPartial("The filter controls, local date entry, back link and browser Back are checked in S5-H03.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks the smallest order detail response.
    // What it does: Reads an unpaid order and checks the deadline, empty lists and initial timeline.
    // Input: The awaiting-payment order from the sample set.
    // Output: A 24-hour deadline and two creation entries without payment or refund rows.
    @Test
    @DisplayName("S5-T03")
    void checkS5T03() {
        JsonNode checkS5T03Order = adminReadSampleOrder(admin(), SharedOrderStatus.AWAITING_PAYMENT);
        Instant createdTime = Instant.parse(checkS5T03Order.path("createdTime").asText());
        Instant paymentDeadline = Instant.parse(checkS5T03Order.path("paymentDeadline").asText());
        assertEquals(Duration.ofHours(24), Duration.between(createdTime, paymentDeadline));
        assertTrue(adminReadOrderRows(checkS5T03Order, "adminViewOrderPaymentList").isEmpty());
        assertTrue(adminReadOrderRows(checkS5T03Order, "adminViewOrderRefundList").isEmpty());
        assertEquals(1, checkS5T03Order.path("adminViewOrderDeliveryDetailVersionCount").asInt());
        List<JsonNode> checkS5T03Timeline = adminReadOrderRows(checkS5T03Order, "adminViewOrderTimelineList");
        assertEquals(2, checkS5T03Timeline.size());
        assertEquals("ORDER_CREATED", checkS5T03Timeline.getFirst().path("adminViewOrderTimelineKind").asText());
        assertEquals("Delivery details version 1 saved.",
                checkS5T03Timeline.getLast().path("adminViewOrderTimelineText").asText());
        reportPartial("Visible deadline, empty boxes and timeline text are checked in S5-H04.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks the order story through delivery.
    // What it does: Checks item math, accepted payment, packing, proof counts and chronologically sorted events.
    // Input: The delivered order from the sample set.
    // Output: Two items with correct prices and total, a named picker and one completed attempt.
    @Test
    @DisplayName("S5-T04")
    void checkS5T04() {
        JsonNode checkS5T04Order = adminReadSampleOrder(admin(), SharedOrderStatus.DELIVERED);
        List<JsonNode> checkS5T04ItemList = adminReadOrderRows(checkS5T04Order, "adminViewOrderItemList");
        assertEquals(2, checkS5T04ItemList.size());
        BigDecimal checkS5T04MugTotal = adminCheckOrderItem(checkS5T04ItemList, "TEST-MUG-", 3);
        BigDecimal checkS5T04PenTotal = adminCheckOrderItem(checkS5T04ItemList, "TEST-PEN-", 1);
        assertEquals(0, checkS5T04MugTotal.add(checkS5T04PenTotal)
                .compareTo(checkS5T04Order.path("totalAmount").decimalValue()));

        JsonNode checkS5T04Payment = adminReadOnlyOrderRow(checkS5T04Order, "adminViewOrderPaymentList");
        assertEquals(SHARED_PAYMENT_STATUS_ACCEPTED, checkS5T04Payment.path("paymentStatus").asText());
        Instant.parse(checkS5T04Payment.path("acceptedTime").asText());
        assertFalse(checkS5T04Payment.has("providerReference"));
        JsonNode checkS5T04Assignment = adminReadOnlyOrderRow(checkS5T04Order, "adminViewOrderPickingAssignmentList");
        assertEquals(userID(checkS5T04Assignment.path("adminViewOrderPickingAssignmentWorkerUsername").asText()),
                checkS5T04Assignment.path("workerUserID").asLong());

        JsonNode checkS5T04Attempt = adminReadOnlyOrderRow(checkS5T04Order, "adminViewOrderDeliveryAttemptList");
        assertEquals(SHARED_ATTEMPT_OUTCOME_DELIVERED, checkS5T04Attempt.path("outcome").asText());
        assertEquals(1, checkS5T04Attempt.path("adminViewOrderDeliveryAttemptPhotoProofCount").asInt());
        assertEquals(1, checkS5T04Attempt.path("adminViewOrderDeliveryAttemptSignatureProofCount").asInt());
        assertFalse(checkS5T04Order.toString().contains("TEMPORARY-NO-FILE"));
        assertFalse(checkS5T04Attempt.has("proofReference"));
        assertFalse(checkS5T04Order.toString().contains("PRIVATE-"), "A tagged private fixture was visible.");

        // The delivered story starts at creation and finishes at the completed attempt.
        List<JsonNode> checkS5T04Timeline = adminReadOrderRows(checkS5T04Order, "adminViewOrderTimelineList");
        assertEquals("ORDER_CREATED", checkS5T04Timeline.getFirst().path("adminViewOrderTimelineKind").asText());
        assertEquals("ATTEMPT_ENDED", checkS5T04Timeline.getLast().path("adminViewOrderTimelineKind").asText());
        assertEquals(checkS5T04Attempt.path("endTime"),
                checkS5T04Timeline.getLast().path("adminViewOrderTimelineTime"));
        Instant checkS5T04PreviousTime = Instant.MIN;
        for (JsonNode checkS5T04Entry : checkS5T04Timeline) {
            Instant checkS5T04Time = Instant.parse(checkS5T04Entry.path("adminViewOrderTimelineTime").asText());
            assertFalse(checkS5T04Time.isBefore(checkS5T04PreviousTime));
            checkS5T04PreviousTime = checkS5T04Time;
        }
        reportPartial("The Items total row, proof count text, and local day and time layout are checked in S5-H04.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks that a worker's note is shown with its private number hidden.
    // What it does: Reads the failed attempt and checks the raw reason and masked text.
    // Input: The failed order from the sample set.
    // Output: A CLIENT_NOT_HOME failure with a masked note.
    @Test
    @DisplayName("S5-T05")
    void checkS5T05() {
        JsonNode checkS5T05Order = adminReadSampleOrder(admin(), SharedOrderStatus.DELIVERY_FAILED);
        JsonNode checkS5T05Attempt = adminReadOnlyOrderRow(checkS5T05Order, "adminViewOrderDeliveryAttemptList");
        assertEquals(SHARED_ATTEMPT_OUTCOME_FAILED, checkS5T05Attempt.path("outcome").asText());
        assertEquals(SHARED_FAILURE_REASON_CLIENT_NOT_HOME, checkS5T05Attempt.path("failureReason").asText());
        assertEquals("Rang the bell twice, call [hidden number]", checkS5T05Attempt.path("internalNote").asText());
        assertFalse(checkS5T05Order.toString().contains("5550 0000 1234"));
        reportPartial("The displayed failure reason and note are checked in S5-H05.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks the separate order, payment and refund values on a cancelled order.
    // What it does: Verifies the linked payment and refund, the full amount and the empty completion time.
    // Input: The cancelled order from the sample set.
    // Output: A requested refund for the accepted payment despite the order's cancelled status.
    @Test
    @DisplayName("S5-T06")
    void checkS5T06() {
        JsonNode checkS5T06ListRow = adminFindSampleOrder(SharedOrderStatus.CANCELLED);
        JsonNode checkS5T06Order = adminReadSampleOrder(admin(), SharedOrderStatus.CANCELLED);
        JsonNode checkS5T06Payment = adminReadOnlyOrderRow(checkS5T06Order, "adminViewOrderPaymentList");
        JsonNode checkS5T06Refund = adminReadOnlyOrderRow(checkS5T06Order, "adminViewOrderRefundList");
        assertEquals(SharedOrderStatus.CANCELLED.name(), checkS5T06Order.path("orderStatus").asText());
        assertEquals(SHARED_PAYMENT_STATUS_ACCEPTED, checkS5T06Payment.path("paymentStatus").asText());
        assertEquals(SHARED_PAYMENT_STATUS_ACCEPTED, checkS5T06ListRow.path(PAYMENT_STATUS_FIELD).asText());
        assertEquals(SHARED_REFUND_STATUS_REQUESTED, checkS5T06Refund.path("refundStatus").asText());
        assertEquals(SHARED_REFUND_STATUS_REQUESTED, checkS5T06ListRow.path(REFUND_STATUS_FIELD).asText());
        assertTrue(checkS5T06Refund.path("refundID").asLong() > 0);
        assertTrue(checkS5T06Payment.path("paymentID").asLong() > 0);
        assertEquals(checkS5T06Payment.path("paymentID"), checkS5T06Refund.path("paymentID"));
        assertEquals(0, checkS5T06Refund.path("amount").decimalValue()
                .compareTo(checkS5T06Order.path("totalAmount").decimalValue()));
        assertEquals(0, checkS5T06Refund.path("amount").decimalValue()
                .compareTo(checkS5T06Payment.path("amount").decimalValue()));
        Instant.parse(checkS5T06Refund.path("createdTime").asText());
        assertTrue(checkS5T06Refund.path("completedTime").isNull());
        assertFalse(checkS5T06Refund.has("providerReference"));
        reportPartial("The Refund table columns, status, references and visible times are checked in S5-H05.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 verifies that stored private values and their field names stay out of order answers.
    // What it does: Confirms the database has private fixtures, then checks three order detail responses.
    // Input: The sample orders, including the failed attempt whose note contains a private test number.
    // Output: No private marker, test number or private delivery field in the responses.
    @Test
    @DisplayName("S5-T07")
    void checkS5T07() {
        assertTrue(count("""
                SELECT count(*) FROM order_delivery_detail WHERE address_text = 'PRIVATE-ADDRESS-MARKER'
                """) >= 10);

        // The failed order includes the private-number fixture, while the other orders cover empty and full details.
        Session checkS5T07Admin = admin();
        for (SharedOrderStatus orderStatus : List.of(SharedOrderStatus.AWAITING_PAYMENT, SharedOrderStatus.DELIVERED,
                SharedOrderStatus.DELIVERY_FAILED)) {
            JsonNode checkS5T07Order = adminReadSampleOrder(checkS5T07Admin, orderStatus);
            String checkS5T07Content = checkS5T07Order.toString();
            assertFalse(checkS5T07Content.contains("PRIVATE"), "A tagged private fixture was visible.");
            assertFalse(checkS5T07Content.contains("5550 0000 1234"), "The private test number was visible.");
            adminCheckOrderPrivateFieldNames(checkS5T07Order);
        }
        reportPartial("Browser Find and the fixed privacy note on three pages are checked in S5-H06.");
    }

    // ===== SECTION 3: DOWNLOAD, PAGINATION AND ROLE CASES =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 tests the Orders download through the live attachment route.
    // What it does: Reads the full file and a filtered download, then verifies the export audit entry.
    // Input: The live Orders list and the sample cancelled order.
    // Output: Matching CSV columns, references, masking and audit data.
    @Test
    @DisplayName("S5-T08")
    void checkS5T08() {
        Session checkS5T08Admin = admin();
        List<String> checkS5T08Export = adminReadOrderCSV(checkS5T08Admin.get(ORDERS + "/csv"), "orders");
        assertEquals(ORDER_CSV_COLUMN_LIST, adminSplitCSVLine(checkS5T08Export.getFirst()));
        assertFalse(checkS5T08Export.stream().anyMatch(checkS5T08Line -> checkS5T08Line.contains("PRIVATE")));
        List<String> checkS5T08ExpectedReferenceList = adminReadOrderPages(checkS5T08Admin, "").stream()
                .map(checkS5T08Row -> checkS5T08Row.path("orderReference").asText()).toList();
        List<String> checkS5T08ExportedReferenceList = checkS5T08Export.stream().skip(1)
                .map(checkS5T08Row -> adminSplitCSVLine(checkS5T08Row).get(1)).toList();
        assertEquals(checkS5T08ExpectedReferenceList, checkS5T08ExportedReferenceList);

        // Use both filters to show the export uses the same matching rules as the list.
        String orderReference = adminFindSampleOrder(SharedOrderStatus.CANCELLED).path("orderReference").asText();
        List<String> checkS5T08FilteredExport = adminReadOrderCSV(checkS5T08Admin.get(ORDERS
                + "/csv?adminSearchOrderKeyword=" + orderReference
                + "&adminSearchOrderOrderStatus=" + SharedOrderStatus.CANCELLED.name()), "orders");
        assertEquals(2, checkS5T08FilteredExport.size());
        assertEquals(orderReference, adminSplitCSVLine(checkS5T08FilteredExport.getLast()).get(1));
        assertTrue(database.sql("""
                SELECT EXISTS (SELECT 1 FROM audit_log
                    WHERE action = 'CSV_DOWNLOADED' AND target_type = 'SYSTEM' AND target_id = 'CSV_ORDERS'
                      AND new_value->>'rowCount' = '1')
                """).query(Boolean.class).single());
        reportPartial("Opening the downloaded file in a spreadsheet is checked in S5-H07.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks tolerance for new shared payment states.
    // What it does: Changes the latest fixture payment temporarily and reads the list row and order detail.
    // Input: A confirmed payment in the isolated database.
    // Output: The unknown status returned unchanged without an API error.
    @Test
    @DisplayName("S5-T09")
    void checkS5T09() {
        Session checkS5T09Admin = admin();
        long paymentID = database.sql("SELECT max(payment_id) FROM payment").query(Long.class).single();
        String paymentStatus = database.sql("SELECT payment_status FROM payment WHERE payment_id = :paymentID")
                .param("paymentID", paymentID).query(String.class).single();
        String orderReference = database.sql("""
                SELECT customer_order.order_reference FROM customer_order
                JOIN payment USING (order_id) WHERE payment_id = :paymentID
                """).param("paymentID", paymentID).query(String.class).single();
        try {
            assertEquals(1, database.sql("""
                    UPDATE payment SET payment_status = :paymentStatus
                    WHERE payment_id = (SELECT max(payment_id) FROM payment)
                    """).param("paymentStatus", UNKNOWN_PAYMENT_STATUS).update());
            JsonNode checkS5T09Row = adminReadOrderPages(checkS5T09Admin,
                    "adminSearchOrderKeyword=" + orderReference).getFirst();
            assertEquals(orderReference, checkS5T09Row.path("orderReference").asText());
            assertEquals(UNKNOWN_PAYMENT_STATUS, checkS5T09Row.path(PAYMENT_STATUS_FIELD).asText());
            long orderID = checkS5T09Row.path("orderID").asLong();
            JsonNode checkS5T09Order = checkS5T09Admin.get(ORDERS + "/" + orderID).expect(200).json();
            assertEquals(UNKNOWN_PAYMENT_STATUS, adminReadOnlyOrderRow(checkS5T09Order, "adminViewOrderPaymentList")
                    .path("paymentStatus").asText());
        } finally {
            database.sql("UPDATE payment SET payment_status = :paymentStatus WHERE payment_id = :paymentID")
                    .param("paymentStatus", paymentStatus).param("paymentID", paymentID).update();
        }
        reportPartial("The grey Something new badge is checked in S5-H05.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks that missing orders give an ordinary page and structured detail error.
    // What it does: Reads the page shell and queries the non-existent order through the detail API.
    // Input: The absent order ID from the case.
    // Output: HTTP 200 for the page and a usable 404 order message from the API.
    @Test
    @DisplayName("S5-T10")
    void checkS5T10() {
        Session checkS5T10Admin = admin();
        String checkS5T10Page = checkS5T10Admin.get(ORDER_DETAIL_PAGE + "?orderID=999999").expect(200).body();
        assertTrue(checkS5T10Page.contains("adminViewOrderMessage"));
        Reply checkS5T10Reply = checkS5T10Admin.get(ORDERS + "/999999").expect(404).code("ADMIN_ORDER_NOT_FOUND");
        assertEquals("Order not found.", checkS5T10Reply.json().path("message").asText());
        reportPartial("Rendering the order not found message on the page is checked in S5-H06.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks page sizes and continuity after two more fixture sets.
    // What it does: Adds twenty orders, reads every list page and checks sizes, count and unique IDs.
    // Input: The first fixture set and the other orders created during the combined check.
    // Output: Ordered, non-overlapping pages with twenty rows on the first page.
    @Test
    @DisplayName("S5-T11")
    void checkS5T11() {
        Session checkS5T11Admin = admin();
        long checkS5T11OriginalCount = count("SELECT count(*) FROM customer_order");
        checkS5T11Admin.tool("sample-order-set", null).expect(200);
        checkS5T11Admin.tool("sample-order-set", null).expect(200);
        List<JsonNode> checkS5T11RowList = adminReadOrderPages(checkS5T11Admin, "");
        assertEquals(checkS5T11OriginalCount + 20, checkS5T11RowList.size());
        assertEquals(checkS5T11RowList.size(),
                checkS5T11RowList.stream().map(checkS5T11Row -> checkS5T11Row.path("orderID").asLong())
                        .distinct().count());
        adminCheckOrderSearchOrder(checkS5T11RowList);
        reportPartial("Clicking Previous, Next and numbered pager controls is checked in S5-H02.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks that no supported Orders endpoint changes order data.
    // What it does: Sends authenticated order write requests and then reads the same order again.
    // Input: The list and one fixture detail route.
    // Output: Shared 405 errors and an unchanged order.
    @Test
    @DisplayName("S5-T12")
    void checkS5T12() {
        Session checkS5T12Admin = admin();
        String checkS5T12OrderPath = ORDERS + "/" + adminFindSampleOrder(SharedOrderStatus.PICKING)
                .path("orderID").asLong();
        JsonNode checkS5T12Before = checkS5T12Admin.get(checkS5T12OrderPath).expect(200).json();
        for (String checkS5T12Method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            checkS5T12Admin.send(checkS5T12Method, ORDERS, null, true).expect(405).code("SHARED_METHOD_NOT_ALLOWED");
            checkS5T12Admin.send(checkS5T12Method, checkS5T12OrderPath, null, true).expect(405)
                    .code("SHARED_METHOD_NOT_ALLOWED");
        }
        assertEquals(checkS5T12Before, checkS5T12Admin.get(checkS5T12OrderPath).expect(200).json());
        reportPartial("Visible absence of order-changing buttons is checked by hand in S5-H02.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 tries the Orders API under the Warehouse Worker account established by the earlier flows.
    // What it does: Signs in as mei_picker and requests the Orders API.
    // Input: The permanent Warehouse Worker's flow password.
    // Output: A 403 SHARED_ACCESS_DENIED response.
    @Test
    @DisplayName("S5-T13")
    void checkS5T13() {
        Session checkS5T13Worker = browser();
        checkS5T13Worker.login("mei_picker", FLOW_PICKER_PASSWORD).expect(200);
        checkS5T13Worker.get(ORDERS).expect(403).code("SHARED_ACCESS_DENIED");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks the order ID now included in every picking scan row.
    // What it does: Opens the scan rows, verifies their common order, and checks the ID in the downloaded file.
    // Input: A picking scan set from the temporary test tool.
    // Output: Six live scan rows and six CSV rows for the same detail page.
    @Test
    @DisplayName("S5-T15")
    void checkS5T15() {
        Session checkS5T15Admin = admin();
        String checkS5T15Message = checkS5T15Admin.tool("picking-scan-rows", null).expect(200).json()
                .path("message").asText();
        var checkS5T15Reference = SAMPLE_ORDER_REFERENCE.matcher(checkS5T15Message);
        assertTrue(checkS5T15Reference.find());
        String orderReference = checkS5T15Reference.group();
        String checkS5T15Filter = "?adminSearchPickingScanHistoryOrderReference=" + orderReference;
        List<JsonNode> checkS5T15ScanRowList = adminReadOrderRows(
                checkS5T15Admin.get(SCANS + checkS5T15Filter).expect(200).json(),
                "adminSearchPickingScanHistoryRowList");
        assertEquals(6, checkS5T15ScanRowList.size());
        long orderID = checkS5T15ScanRowList.getFirst().path("orderID").asLong();
        assertTrue(orderID > 0);
        for (JsonNode checkS5T15Row : checkS5T15ScanRowList) {
            assertEquals(orderID, checkS5T15Row.path("orderID").asLong());
            assertEquals(orderReference, checkS5T15Row.path("adminSearchPickingScanHistoryOrderReference").asText());
        }
        assertEquals(orderReference,
                checkS5T15Admin.get(ORDERS + "/" + orderID).expect(200).json().path("orderReference").asText());
        checkS5T15Admin.get(ORDER_DETAIL_PAGE + "?orderID=" + orderID).expect(200);

        List<String> checkS5T15Export = adminReadOrderCSV(
                checkS5T15Admin.get(SCANS + "/csv" + checkS5T15Filter), "picking-scan-history");
        assertEquals(7, checkS5T15Export.size());
        int checkS5T15OrderIndex = adminSplitCSVLine(checkS5T15Export.getFirst()).indexOf("orderID");
        assertTrue(checkS5T15OrderIndex >= 0);
        for (String checkS5T15Line : checkS5T15Export.subList(1, checkS5T15Export.size())) {
            assertEquals(Long.toString(orderID), adminSplitCSVLine(checkS5T15Line).get(checkS5T15OrderIndex));
        }
        reportPartial("The scan links and Back to logs with the retained tab and filter are checked in S5-H08.");
    }

    // ===== SECTION 4: VERIFICATION HELPERS =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 reads actual API pages to compare search and export results.
    // What it does: Follows page numbers and checks the total count, page size and reported page number.
    // Input: An Admin session and a URL query without the page number.
    // Output: Every matching row, in server order.
    private List<JsonNode> adminReadOrderPages(Session adminReadOrderPagesSession, String adminReadOrderPagesFilter) {
        String adminReadOrderPagesRequest = ORDERS + "?";
        if (!adminReadOrderPagesFilter.isEmpty()) {
            adminReadOrderPagesRequest += adminReadOrderPagesFilter + "&";
        }
        List<JsonNode> adminReadOrderPagesRowList = new ArrayList<>();
        int adminReadOrderPagesCount = 1;
        int adminReadOrderPagesTotal = 0;
        for (int adminReadOrderPagesNumber = 1; adminReadOrderPagesNumber <= adminReadOrderPagesCount;
                adminReadOrderPagesNumber++) {
            JsonNode adminReadOrderPagesResponse = adminReadOrderPagesSession
                    .get(adminReadOrderPagesRequest + "adminSearchOrderPageNumber=" + adminReadOrderPagesNumber)
                    .expect(200).json();
            int adminReadOrderPagesSize = adminReadOrderPagesResponse.path("adminSearchOrderTotalRowCount").asInt(-1);
            assertTrue(adminReadOrderPagesSize >= 0);
            if (adminReadOrderPagesNumber == 1) {
                adminReadOrderPagesCount = adminReadOrderPagesResponse.path("adminSearchOrderTotalPageCount").asInt();
                assertEquals(Math.max(1, (adminReadOrderPagesSize + 19) / 20), adminReadOrderPagesCount);
                adminReadOrderPagesTotal = adminReadOrderPagesSize;
            }
            assertEquals(adminReadOrderPagesTotal, adminReadOrderPagesSize);
            assertEquals(adminReadOrderPagesNumber,
                    adminReadOrderPagesResponse.path("adminSearchOrderPageNumber").asInt());
            List<JsonNode> adminReadOrderPagesCurrentList =
                    adminReadOrderRows(adminReadOrderPagesResponse, "adminSearchOrderRowList");
            assertEquals(Math.min(20, adminReadOrderPagesTotal - adminReadOrderPagesRowList.size()),
                    adminReadOrderPagesCurrentList.size());
            adminReadOrderPagesRowList.addAll(adminReadOrderPagesCurrentList);
        }
        assertEquals(adminReadOrderPagesTotal, adminReadOrderPagesRowList.size());
        return adminReadOrderPagesRowList;
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 compares API row lists without assuming a JSON array is present.
    // What it does: Reads a named array and converts its rows to a list.
    // Input: A parsed response and the array field name. Output: The rows.
    private List<JsonNode> adminReadOrderRows(JsonNode adminReadOrderRowsResponse, String adminReadOrderRowsField) {
        JsonNode adminReadOrderRowsArray = adminReadOrderRowsResponse.path(adminReadOrderRowsField);
        assertTrue(adminReadOrderRowsArray.isArray(), adminReadOrderRowsField);
        List<JsonNode> adminReadOrderRowsList = new ArrayList<>();
        adminReadOrderRowsArray.forEach(adminReadOrderRowsList::add);
        return adminReadOrderRowsList;
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 detects private field names anywhere within an order answer.
    // What it does: Checks property names and recursively visits objects and arrays below them.
    // Input: A JSON answer or nested value. Output: An assertion that the value has no private delivery field names.
    private void adminCheckOrderPrivateFieldNames(JsonNode adminCheckOrderPrivateFieldNamesNode) {
        // Check keys separately so mentions of these words in ordinary public text do not count.
        if (adminCheckOrderPrivateFieldNamesNode.isObject()) {
            for (var adminCheckOrderPrivateFieldNamesProperty : adminCheckOrderPrivateFieldNamesNode.properties()) {
                String adminCheckOrderPrivateFieldNamesKey =
                        adminCheckOrderPrivateFieldNamesProperty.getKey().toLowerCase(Locale.ROOT);
                assertFalse(adminCheckOrderPrivateFieldNamesKey.contains("phone")
                        || adminCheckOrderPrivateFieldNamesKey.contains("address")
                        || adminCheckOrderPrivateFieldNamesKey.contains("recipient"),
                        "Private delivery field in JSON.");
            }
        }
        for (JsonNode adminCheckOrderPrivateFieldNamesChild : adminCheckOrderPrivateFieldNamesNode) {
            adminCheckOrderPrivateFieldNames(adminCheckOrderPrivateFieldNamesChild);
        }
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 expects a single payment, refund or attempt in selected sample details.
    // What it does: Verifies that a named response array has exactly one row.
    // Input: A parsed response and the array field name. Output: The only row.
    private JsonNode adminReadOnlyOrderRow(JsonNode adminReadOnlyOrderRowAnswer, String adminReadOnlyOrderRowField) {
        List<JsonNode> adminReadOnlyOrderRowList = adminReadOrderRows(adminReadOnlyOrderRowAnswer,
                adminReadOnlyOrderRowField);
        assertEquals(1, adminReadOnlyOrderRowList.size(), adminReadOnlyOrderRowField);
        return adminReadOnlyOrderRowList.getFirst();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 finds a sample row without assuming specific database IDs.
    // What it does: Finds the first sample set's one row in a requested order state.
    // Input: A known order status. Output: The matching search row.
    private JsonNode adminFindSampleOrder(SharedOrderStatus orderStatus) {
        assertNotNull(adminCheckSampleOrderRowList, "S5-T01 must create the fixture set first.");
        List<JsonNode> adminFindSampleOrderMatchList = adminCheckSampleOrderRowList.stream()
                .filter(adminFindSampleOrderRow -> orderStatus.name().equals(adminFindSampleOrderRow
                        .path("orderStatus").asText())).toList();
        assertEquals(1, adminFindSampleOrderMatchList.size(), orderStatus.name());
        return adminFindSampleOrderMatchList.getFirst();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 reads the order data using its real API.
    // What it does: Requests the detail route for one sample status.
    // Input: The signed-in Admin and the selected fixture status. Output: The corresponding order detail.
    private JsonNode adminReadSampleOrder(Session adminReadSampleOrderSession, SharedOrderStatus orderStatus) {
        JsonNode adminReadSampleOrderRow = adminFindSampleOrder(orderStatus);
        long orderID = adminReadSampleOrderRow.path("orderID").asLong();
        assertTrue(orderID > 0);
        JsonNode adminReadSampleOrderDetail = adminReadSampleOrderSession.get(ORDERS + "/" + orderID)
                .expect(200).json();
        assertEquals(adminReadSampleOrderRow.path("orderReference"), adminReadSampleOrderDetail.path("orderReference"));
        return adminReadSampleOrderDetail;
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks newest-first ordering and stable order for matching timestamps.
    // What it does: Compares timestamps and breaks ties with the order ID.
    // Input: An ordered API row list. Output: Assertions for every adjacent pair.
    private void adminCheckOrderSearchOrder(List<JsonNode> adminCheckOrderSearchOrderRowList) {
        Instant adminCheckOrderSearchOrderPreviousTime = Instant.MAX;
        long adminCheckOrderSearchOrderPreviousID = Long.MAX_VALUE;
        for (JsonNode adminCheckOrderSearchOrderRow : adminCheckOrderSearchOrderRowList) {
            Instant createdTime = Instant.parse(adminCheckOrderSearchOrderRow.path("createdTime").asText());
            long orderID = adminCheckOrderSearchOrderRow.path("orderID").asLong();
            assertFalse(createdTime.isAfter(adminCheckOrderSearchOrderPreviousTime));
            if (createdTime.equals(adminCheckOrderSearchOrderPreviousTime)) {
                assertTrue(orderID < adminCheckOrderSearchOrderPreviousID);
            }
            adminCheckOrderSearchOrderPreviousTime = createdTime;
            adminCheckOrderSearchOrderPreviousID = orderID;
        }
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 verifies a filtered result and then clears it.
    // What it does: Compares the filtered pages to the same subset of unfiltered results.
    // Input: An Admin session, all order rows, the filter query and the expected matching rule.
    // Output: Assertions for the filtered rows and the cleared search.
    private void adminCheckOrderFilter(Session adminCheckOrderFilterSession, List<JsonNode> adminCheckOrderFilterBase,
            String adminCheckOrderFilterQuery, Predicate<JsonNode> adminCheckOrderFilterMatches) {
        List<JsonNode> adminCheckOrderFilterExpected = adminCheckOrderFilterBase.stream()
                .filter(adminCheckOrderFilterMatches).toList();
        assertFalse(adminCheckOrderFilterExpected.isEmpty());
        assertEquals(adminCheckOrderFilterExpected, adminReadOrderPages(adminCheckOrderFilterSession,
                adminCheckOrderFilterQuery), adminCheckOrderFilterQuery);
        assertEquals(adminCheckOrderFilterBase, adminReadOrderPages(adminCheckOrderFilterSession, ""));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 checks each fixture line total.
    // What it does: Finds a fixture product by SKU prefix and checks its price and quantity.
    // Input: The item rows, the product prefix and the expected quantity. Output: The checked line total.
    private BigDecimal adminCheckOrderItem(List<JsonNode> adminCheckOrderItemList, String adminCheckOrderItemPrefix,
            int adminCheckOrderItemQuantity) {
        JsonNode adminCheckOrderItemMatch = adminCheckOrderItemList.stream()
                .filter(adminCheckOrderItemRow -> adminCheckOrderItemRow.path("adminViewOrderItemSKU").asText()
                        .startsWith(adminCheckOrderItemPrefix)).findFirst().orElseThrow();
        assertEquals(adminCheckOrderItemQuantity, adminCheckOrderItemMatch.path("quantity").asInt());
        BigDecimal unitPrice = adminCheckOrderItemMatch.path("unitPrice").decimalValue();
        assertTrue(unitPrice.signum() > 0);
        BigDecimal adminViewOrderItemLineTotal = adminCheckOrderItemMatch.path("adminViewOrderItemLineTotal")
                .decimalValue();
        assertEquals(0, adminViewOrderItemLineTotal.compareTo(unitPrice.multiply(
                BigDecimal.valueOf(adminCheckOrderItemQuantity))));
        return adminViewOrderItemLineTotal;
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 verifies live CSV attachments and strips the marker for reading their visible fields.
    // What it does: Checks the file type, name and start mark.
    // Input: The response and filename stem. Output: Lines from the UTF-8 CSV.
    private List<String> adminReadOrderCSV(Reply adminReadOrderCSVReply, String adminReadOrderCSVFileStem) {
        assertTrue(adminReadOrderCSVReply.expect(200).header("Content-Type").startsWith("text/csv"));
        assertTrue(adminReadOrderCSVReply.header("Content-Disposition")
                .matches("attachment;.*admin-" + adminReadOrderCSVFileStem + "-[0-9]{8}-[0-9]{6}\\.csv.*"));
        String adminReadOrderCSVText = adminReadOrderCSVReply.body();
        assertTrue(adminReadOrderCSVText.startsWith("\uFEFF"));
        return adminReadOrderCSVText.substring(1).lines().toList();
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 5 reads CSV lines from fixtures whose fields may contain commas but no quote-delimiter sequences.
    // What it does: Separates quoted fixture fields while preserving empty values.
    // Input: One complete line from the shared CSV writer. Output: The fixture's fields in column order.
    private List<String> adminSplitCSVLine(String adminSplitCSVLineText) {
        assertTrue(adminSplitCSVLineText.startsWith("\"") && adminSplitCSVLineText.endsWith("\""));
        return Arrays.asList(adminSplitCSVLineText.substring(1, adminSplitCSVLineText.length() - 1)
                .split("\",\"", -1));
    }
}
