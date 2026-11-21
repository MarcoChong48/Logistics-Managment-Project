package com.lms.backend.admin.order;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_CSV_MAXIMUM_ROW_COUNT;
import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.admin.common.AdminPrivateValueRule.adminHidePrivateValue;
import static com.lms.backend.shared.csv.SharedCSVWriter.sharedWriteCSVDownload;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_CSV_DOWNLOADED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_STATUS_PENDING;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.shortage.AdminShortageCaseStatus;
import com.lms.backend.shared.database.ClientInstruction;
import com.lms.backend.shared.database.ClientInstructionRepository;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.DeliveryAssignmentHistoryRepository;
import com.lms.backend.shared.database.DeliveryAttempt;
import com.lms.backend.shared.database.DeliveryAttemptRepository;
import com.lms.backend.shared.database.DeliveryPackage;
import com.lms.backend.shared.database.DeliveryPackageRepository;
import com.lms.backend.shared.database.OrderDeliveryDetail;
import com.lms.backend.shared.database.OrderDeliveryDetailRepository;
import com.lms.backend.shared.database.OrderItem;
import com.lms.backend.shared.database.OrderItemRepository;
import com.lms.backend.shared.database.PackageCustodyEventRepository;
import com.lms.backend.shared.database.PaymentRepository;
import com.lms.backend.shared.database.PickingAssignmentHistoryRepository;
import com.lms.backend.shared.database.PickingTask;
import com.lms.backend.shared.database.PickingTaskRepository;
import com.lms.backend.shared.database.RefundRepository;
import com.lms.backend.shared.database.ShortageCase;
import com.lms.backend.shared.database.ShortageCaseRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedProofType;

@Service
public class AdminOrderService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final CustomerOrderRepository adminOrderRepository;
    private final OrderDeliveryDetailRepository adminViewOrderDeliveryDetailRepository;
    private final OrderItemRepository adminViewOrderItemRepository;
    private final PaymentRepository adminViewOrderPaymentRepository;
    private final RefundRepository adminViewOrderRefundRepository;
    private final PickingTaskRepository adminViewOrderPickingTaskRepository;
    private final PickingAssignmentHistoryRepository adminViewOrderPickingAssignmentHistoryRepository;
    private final DeliveryPackageRepository adminViewOrderPackageRepository;
    private final PackageCustodyEventRepository adminViewOrderCustodyEventRepository;
    private final DeliveryAssignmentHistoryRepository adminViewOrderDeliveryAssignmentHistoryRepository;
    private final DeliveryAttemptRepository adminViewOrderDeliveryAttemptRepository;
    private final ClientInstructionRepository adminViewOrderClientInstructionRepository;
    private final ShortageCaseRepository adminViewOrderShortageCaseRepository;
    private final SharedCurrentUserReader adminDownloadOrderCSVCurrentUserReader;
    private final SharedAuditLogWriter adminDownloadOrderCSVAuditLogWriter;

    /**
     * What it does: Connects the order views to their shared tables and the CSV audit writer.
     * Input: Order and related repositories, the current user reader and audit writer.
     * Output: An order service.
     */
    public AdminOrderService(CustomerOrderRepository adminOrderRepository,
            OrderDeliveryDetailRepository adminViewOrderDeliveryDetailRepository,
            OrderItemRepository adminViewOrderItemRepository, PaymentRepository adminViewOrderPaymentRepository,
            RefundRepository adminViewOrderRefundRepository, PickingTaskRepository adminViewOrderPickingTaskRepository,
            PickingAssignmentHistoryRepository adminViewOrderPickingAssignmentHistoryRepository,
            DeliveryPackageRepository adminViewOrderPackageRepository,
            PackageCustodyEventRepository adminViewOrderCustodyEventRepository,
            DeliveryAssignmentHistoryRepository adminViewOrderDeliveryAssignmentHistoryRepository,
            DeliveryAttemptRepository adminViewOrderDeliveryAttemptRepository,
            ClientInstructionRepository adminViewOrderClientInstructionRepository,
            ShortageCaseRepository adminViewOrderShortageCaseRepository,
            SharedCurrentUserReader adminDownloadOrderCSVCurrentUserReader,
            SharedAuditLogWriter adminDownloadOrderCSVAuditLogWriter) {
        this.adminOrderRepository = adminOrderRepository;
        this.adminViewOrderDeliveryDetailRepository = adminViewOrderDeliveryDetailRepository;
        this.adminViewOrderItemRepository = adminViewOrderItemRepository;
        this.adminViewOrderPaymentRepository = adminViewOrderPaymentRepository;
        this.adminViewOrderRefundRepository = adminViewOrderRefundRepository;
        this.adminViewOrderPickingTaskRepository = adminViewOrderPickingTaskRepository;
        this.adminViewOrderPickingAssignmentHistoryRepository = adminViewOrderPickingAssignmentHistoryRepository;
        this.adminViewOrderPackageRepository = adminViewOrderPackageRepository;
        this.adminViewOrderCustodyEventRepository = adminViewOrderCustodyEventRepository;
        this.adminViewOrderDeliveryAssignmentHistoryRepository = adminViewOrderDeliveryAssignmentHistoryRepository;
        this.adminViewOrderDeliveryAttemptRepository = adminViewOrderDeliveryAttemptRepository;
        this.adminViewOrderClientInstructionRepository = adminViewOrderClientInstructionRepository;
        this.adminViewOrderShortageCaseRepository = adminViewOrderShortageCaseRepository;
        this.adminDownloadOrderCSVCurrentUserReader = adminDownloadOrderCSVCurrentUserReader;
        this.adminDownloadOrderCSVAuditLogWriter = adminDownloadOrderCSVAuditLogWriter;
    }

    // ===== SECTION 2: adminSearchOrder =====

    /**
     * What it does: Lists matching orders and their separate financial states, newest first.
     * Input: The optional filters and the one-based page number.
     * Output: Up to 20 rows and the page counts.
     * Placeholder: PI-08 and PI-05. Roles populate shared rows and supply their state words.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchOrderResponse adminSearchOrder(String adminSearchOrderKeyword,
            String adminSearchOrderOrderStatus, Instant adminSearchOrderCreatedFromTime,
            Instant adminSearchOrderCreatedToTime, int adminSearchOrderPageNumber) {
        // Clamp out-of-range page requests in the same snapshot as the count.
        int adminSearchOrderRequestedPageNumber =
                Math.clamp(adminSearchOrderPageNumber, 1, Integer.MAX_VALUE / ADMIN_TABLE_PAGE_SIZE);
        Page<AdminSearchOrderRow> adminSearchOrderPage = adminReadOrderPage(adminSearchOrderKeyword,
                adminSearchOrderOrderStatus, adminSearchOrderCreatedFromTime, adminSearchOrderCreatedToTime,
                PageRequest.of(adminSearchOrderRequestedPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
        long adminSearchOrderTotalRowCount = adminSearchOrderPage.getTotalElements();
        int adminSearchOrderTotalPageCount = adminCountPage(adminSearchOrderTotalRowCount);
        int adminSearchOrderActualPageNumber = Math.min(adminSearchOrderRequestedPageNumber,
                adminSearchOrderTotalPageCount);
        if (adminSearchOrderActualPageNumber < adminSearchOrderRequestedPageNumber
                && adminSearchOrderTotalRowCount > 0) {
            adminSearchOrderPage = adminReadOrderPage(adminSearchOrderKeyword, adminSearchOrderOrderStatus,
                    adminSearchOrderCreatedFromTime, adminSearchOrderCreatedToTime,
                    PageRequest.of(adminSearchOrderActualPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
        }
        return new AdminSearchOrderResponse(adminSearchOrderPage.getContent(), adminSearchOrderActualPageNumber,
                adminSearchOrderTotalPageCount, adminSearchOrderTotalRowCount);
    }

    // ===== SECTION 3: adminViewOrder =====

    /**
     * What it does: Reads the order and builds its public detail sections and timeline.
     * Input: The order ID.
     * Output: Order detail, or 404 ADMIN_ORDER_NOT_FOUND.
     * Placeholder: PI-08 and PI-05. Roles populate shared rows and supply their state words.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminViewOrderResponse adminViewOrder(Long orderID) {
        // PLACEHOLDER INPUT PI-08 and PI-05
        // From: Client, Payment, Warehouse and Delivery.
        // What: The order, related history and provisional state words.
        // How to use: Role owners write shared rows. The test tools currently populate synthetic examples.
        CustomerOrder adminViewOrderOrder = adminOrderRepository.findById(orderID).orElseThrow(
                () -> new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_ORDER_NOT_FOUND", "Order not found."));
        String adminViewOrderClientUsername =
                adminOrderRepository.adminViewOrderClientUsername(adminViewOrderOrder.clientUserID);
        List<OrderDeliveryDetail> adminViewOrderDeliveryDetailList =
                adminViewOrderDeliveryDetailRepository.findByOrderIDOrderByVersionNo(orderID);

        // Read only public payment fields and use each item's historical price.
        List<AdminViewOrderItemRow> adminViewOrderItemList =
                adminViewOrderItemRepository.adminViewOrderItems(orderID)
                        .stream().map(this::adminReadOrderItem).toList();
        List<AdminViewOrderPaymentRow> adminViewOrderPaymentList =
                adminViewOrderPaymentRepository.adminViewOrderPayments(orderID);
        List<AdminViewOrderRefundRow> adminViewOrderRefundList =
                adminViewOrderRefundRepository.adminViewOrderRefunds(orderID);
        AdminViewOrderPickingTask adminViewOrderPickingTask =
                adminViewOrderPickingTaskRepository.findByOrderID(orderID).map(this::adminReadOrderPickingTask)
                        .orElse(null);
        List<AdminViewOrderPickingAssignmentRow> adminViewOrderPickingAssignmentList = adminViewOrderPickingTask == null
                ? List.of() : adminViewOrderPickingAssignmentHistoryRepository.adminViewOrderPickingAssignments(
                        adminViewOrderPickingTask.pickingTaskID());

        // Responsibility can move without changing the handler or package custody.
        List<Object[]> adminViewOrderFoundPackageList = adminViewOrderPackageRepository.adminViewOrderPackage(orderID);
        Object[] adminViewOrderPackageValues = adminViewOrderFoundPackageList.isEmpty()
                ? null : adminViewOrderFoundPackageList.getFirst();
        Long packageID = adminViewOrderPackageValues == null ? null
                : ((DeliveryPackage) adminViewOrderPackageValues[0]).packageID;
        List<Object[]> adminViewOrderDeliveryAssignmentList = packageID == null ? List.of()
                : adminViewOrderDeliveryAssignmentHistoryRepository.adminViewOrderDeliveryAssignments(packageID);
        AdminViewOrderPackage adminViewOrderPackage = adminReadOrderPackage(adminViewOrderPackageValues,
                adminViewOrderDeliveryAssignmentList);
        List<Object[]> adminViewOrderCustodyEventList = packageID == null ? List.of()
                : adminViewOrderCustodyEventRepository.adminViewOrderCustodyEvents(packageID);
        List<AdminViewOrderDeliveryAttemptRow> adminViewOrderDeliveryAttemptList = packageID == null ? List.of()
                : adminViewOrderDeliveryAttemptRepository.adminViewOrderDeliveryAttempts(packageID,
                        SharedProofType.PHOTO.name(), SharedProofType.SIGNATURE.name())
                        .stream().map(this::adminReadOrderDeliveryAttempt).toList();

        // Build timeline entries from the rows already read for this view.
        List<AdminViewOrderClientInstructionRow> adminViewOrderClientInstructionList =
                adminViewOrderClientInstructionRepository.adminViewOrderClientInstructions(orderID)
                        .stream().map(this::adminReadOrderClientInstruction).toList();
        List<Object[]> adminViewOrderShortageCaseValuesList =
                adminViewOrderShortageCaseRepository.adminViewOrderShortageCases(orderID);
        List<AdminViewOrderShortageCaseRow> adminViewOrderShortageCaseList =
                adminViewOrderShortageCaseValuesList.stream().map(this::adminReadOrderShortageCase).toList();
        List<AdminViewOrderTimelineRow> adminViewOrderTimelineList = AdminOrderTimelineBuilder.adminBuildOrderTimeline(
                adminViewOrderOrder, adminViewOrderClientUsername, adminViewOrderDeliveryDetailList,
                adminViewOrderPaymentList, adminViewOrderRefundList, adminViewOrderPickingTask,
                adminViewOrderPickingAssignmentList, adminViewOrderCustodyEventList, adminViewOrderDeliveryAttemptList,
                adminViewOrderClientInstructionList, adminViewOrderShortageCaseValuesList,
                adminViewOrderDeliveryAssignmentList);
        return new AdminViewOrderResponse(orderID, adminViewOrderOrder.orderReference, adminViewOrderOrder.clientUserID,
                adminViewOrderClientUsername, adminViewOrderOrder.orderStatus, adminViewOrderOrder.createdTime,
                adminViewOrderOrder.paymentDeadline, adminViewOrderOrder.totalAmount,
                adminViewOrderDeliveryDetailList.size(), adminViewOrderItemList, adminViewOrderPaymentList,
                adminViewOrderRefundList, adminViewOrderPickingTask, adminViewOrderPickingAssignmentList,
                adminViewOrderPackage, adminViewOrderDeliveryAttemptList, adminViewOrderClientInstructionList,
                adminViewOrderShortageCaseList, adminViewOrderTimelineList);
    }

    // ===== SECTION 4: adminDownloadOrderCSV =====

    /**
     * What it does: Exports the newest matching orders and writes one download audit row.
     * Input: The same filters as order search, without a page number.
     * Output: A UTF-8 attachment with at most 10,000 data rows.
     * Placeholder: PI-08 and PI-05. Shares the order search's source data and state words.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> adminDownloadOrderCSV(String adminSearchOrderKeyword,
            String adminSearchOrderOrderStatus, Instant adminSearchOrderCreatedFromTime,
            Instant adminSearchOrderCreatedToTime) {
        // Use the search query once with the larger export window.
        Page<AdminSearchOrderRow> adminDownloadOrderCSVPage = adminReadOrderPage(adminSearchOrderKeyword,
                adminSearchOrderOrderStatus, adminSearchOrderCreatedFromTime, adminSearchOrderCreatedToTime,
                PageRequest.of(0, ADMIN_CSV_MAXIMUM_ROW_COUNT));
        List<List<Object>> adminDownloadOrderCSVRowList = adminDownloadOrderCSVPage.stream()
                .map(adminDownloadOrderCSVRow -> Arrays.<Object>asList(adminDownloadOrderCSVRow.orderID(),
                        adminDownloadOrderCSVRow.orderReference(), adminDownloadOrderCSVRow.clientUserID(),
                        adminDownloadOrderCSVRow.adminSearchOrderClientUsername(),
                        adminDownloadOrderCSVRow.orderStatus(),
                        adminDownloadOrderCSVRow.createdTime(), adminDownloadOrderCSVRow.paymentDeadline(),
                        adminDownloadOrderCSVRow.totalAmount(),
                        adminDownloadOrderCSVRow.adminSearchOrderLatestPaymentStatus(),
                        adminDownloadOrderCSVRow.adminSearchOrderLatestRefundStatus(),
                        adminDownloadOrderCSVRow.adminSearchOrderHasOpenCase())).toList();

        // Keep the search filters in the audit entry, with free-form text masked.
        Map<String, Object> adminDownloadOrderCSVFilterMap = new LinkedHashMap<>();
        if (adminSearchOrderKeyword != null && !adminSearchOrderKeyword.isBlank()) {
            adminDownloadOrderCSVFilterMap.put("adminSearchOrderKeyword",
                    adminHidePrivateValue(adminSearchOrderKeyword));
        }
        if (adminSearchOrderOrderStatus != null && !adminSearchOrderOrderStatus.isBlank()) {
            adminDownloadOrderCSVFilterMap.put("adminSearchOrderOrderStatus",
                    adminHidePrivateValue(adminSearchOrderOrderStatus.strip()));
        }
        if (adminSearchOrderCreatedFromTime != null) {
            adminDownloadOrderCSVFilterMap.put("adminSearchOrderCreatedFromTime", adminSearchOrderCreatedFromTime);
        }
        if (adminSearchOrderCreatedToTime != null) {
            adminDownloadOrderCSVFilterMap.put("adminSearchOrderCreatedToTime", adminSearchOrderCreatedToTime);
        }
        adminDownloadOrderCSVAuditLogWriter.sharedWriteAuditLog(
                adminDownloadOrderCSVCurrentUserReader.sharedReadCurrentUser().userID(),
                SHARED_AUDIT_ACTION_CSV_DOWNLOADED, "SYSTEM", "CSV_ORDERS", null,
                Map.of("rowCount", adminDownloadOrderCSVRowList.size(), "filters", adminDownloadOrderCSVFilterMap));

        String adminDownloadOrderCSVLimitMessage =
                adminDownloadOrderCSVPage.getTotalElements() > ADMIN_CSV_MAXIMUM_ROW_COUNT
                        ? "Only the newest 10000 rows are included." : null;
        return sharedWriteCSVDownload(List.of("orderID", "orderReference", "clientUserID",
                "adminSearchOrderClientUsername", "orderStatus", "createdTime", "paymentDeadline", "totalAmount",
                "adminSearchOrderLatestPaymentStatus", "adminSearchOrderLatestRefundStatus",
                "adminSearchOrderHasOpenCase"), adminDownloadOrderCSVRowList, "admin-orders", Instant.now(),
                adminDownloadOrderCSVLimitMessage);
    }

    // ===== SECTION 5: HELPER FUNCTIONS =====

    /**
     * What it does: Applies the same literal keyword and date filters for the list and export.
     * Input: The supplied filters and page window.
     * Output: Matching orders in newest-first order.
     * Placeholder: PI-08 provides the stored rows and PI-05 the request state.
     */
    private Page<AdminSearchOrderRow> adminReadOrderPage(String adminReadOrderPageKeyword,
            String adminReadOrderPageOrderStatus, Instant adminReadOrderPageCreatedFromTime,
            Instant adminReadOrderPageCreatedToTime, PageRequest adminReadOrderPageRequest) {
        // An empty keyword matches every order and an empty status leaves it unfiltered.
        String adminReadOrderPageKeywordText = adminReadOrderPageKeyword == null
                ? "" : adminReadOrderPageKeyword.strip();
        String adminReadOrderPageStatusText =
                adminReadOrderPageOrderStatus == null || adminReadOrderPageOrderStatus.isBlank()
                        ? null : adminReadOrderPageOrderStatus.strip();

        // PLACEHOLDER INPUT PI-08 and PI-05
        // From: Client, Payment, Warehouse and Delivery.
        // What: The shared orders and related rows, including the provisional pending request state.
        // How to use: Role owners write these tables. The test tools currently provide synthetic rows.
        return adminOrderRepository.adminSearchOrder(adminReadOrderPageKeywordText, adminReadOrderPageStatusText,
                adminReadOrderPageCreatedFromTime, adminReadOrderPageCreatedToTime,
                List.of(AdminShortageCaseStatus.OPEN.name(), AdminShortageCaseStatus.WAITING_RESTOCK.name()),
                SHARED_INSTRUCTION_STATUS_PENDING, adminReadOrderPageRequest);
    }

    /**
     * What it does: Builds a public item row and computes its extended price.
     * Input: The item, SKU and product name.
     * Output: The historical quantities and prices, including the line total.
     */
    private AdminViewOrderItemRow adminReadOrderItem(Object[] adminReadOrderItemValues) {
        OrderItem adminReadOrderItemRow = (OrderItem) adminReadOrderItemValues[0];
        return new AdminViewOrderItemRow(adminReadOrderItemRow.orderItemID, adminReadOrderItemRow.productID,
                (String) adminReadOrderItemValues[1], (String) adminReadOrderItemValues[2],
                adminReadOrderItemRow.quantity, adminReadOrderItemRow.unitPrice, adminReadOrderItemRow.pickedQuantity,
                adminReadOrderItemRow.unitPrice.multiply(BigDecimal.valueOf(adminReadOrderItemRow.quantity)));
    }

    /**
     * What it does: Builds the public picking task section.
     * Input: The order's task.
     * Output: The task's public fields.
     */
    private AdminViewOrderPickingTask adminReadOrderPickingTask(PickingTask adminReadOrderPickingTaskRow) {
        return new AdminViewOrderPickingTask(adminReadOrderPickingTaskRow.pickingTaskID,
                adminReadOrderPickingTaskRow.taskStatus, adminReadOrderPickingTaskRow.startTime,
                adminReadOrderPickingTaskRow.confirmPackingTime);
    }

    /**
     * What it does: Finds the responsible worker independently of custody.
     * Input: The optional package with handler and its delivery assignments, newest first.
     * Output: The public package section, or null.
     */
    private AdminViewOrderPackage adminReadOrderPackage(Object[] adminReadOrderPackageValues,
            List<Object[]> adminReadOrderPackageAssignmentList) {
        if (adminReadOrderPackageValues == null) {
            return null;
        }
        DeliveryPackage adminReadOrderPackageRow = (DeliveryPackage) adminReadOrderPackageValues[0];
        String adminReadOrderPackageHandler = (String) adminReadOrderPackageValues[1];
        String adminReadOrderPackageResponsibleWorker = adminReadOrderPackageAssignmentList.isEmpty()
                ? adminReadOrderPackageHandler : (String) adminReadOrderPackageAssignmentList.getFirst()[2];
        return new AdminViewOrderPackage(adminReadOrderPackageRow.packageID, adminReadOrderPackageRow.packageQRCode,
                adminReadOrderPackageRow.custodyStatus, adminReadOrderPackageRow.currentHandlerUserID,
                adminReadOrderPackageHandler, adminReadOrderPackageResponsibleWorker);
    }

    /**
     * What it does: Builds a delivery attempt row with a masked note and proof counts.
     * Input: The attempt, worker username and photo and signature counts.
     * Output: An attempt with no proof file references.
     */
    private AdminViewOrderDeliveryAttemptRow adminReadOrderDeliveryAttempt(
            Object[] adminReadOrderDeliveryAttemptValues) {
        DeliveryAttempt adminReadOrderDeliveryAttemptRow = (DeliveryAttempt) adminReadOrderDeliveryAttemptValues[0];
        return new AdminViewOrderDeliveryAttemptRow(adminReadOrderDeliveryAttemptRow.deliveryAttemptID,
                adminReadOrderDeliveryAttemptRow.deliveryWorkerID, (String) adminReadOrderDeliveryAttemptValues[1],
                adminReadOrderDeliveryAttemptRow.startTime, adminReadOrderDeliveryAttemptRow.endTime,
                adminReadOrderDeliveryAttemptRow.outcome,
                adminHidePrivateValue(adminReadOrderDeliveryAttemptRow.failureReason),
                adminHidePrivateValue(adminReadOrderDeliveryAttemptRow.internalNote),
                (Long) adminReadOrderDeliveryAttemptValues[2], (Long) adminReadOrderDeliveryAttemptValues[3]);
    }

    /**
     * What it does: Builds the public client request row.
     * Input: The saved request with its recorder and resolver usernames.
     * Output: The public request fields and masked resolution note.
     */
    private AdminViewOrderClientInstructionRow adminReadOrderClientInstruction(
            Object[] adminReadOrderClientInstructionValues) {
        ClientInstruction adminReadOrderClientInstructionRow =
                (ClientInstruction) adminReadOrderClientInstructionValues[0];
        return new AdminViewOrderClientInstructionRow(adminReadOrderClientInstructionRow.instructionID,
                adminReadOrderClientInstructionRow.instructionType, adminReadOrderClientInstructionRow.status,
                adminReadOrderClientInstructionRow.source, adminReadOrderClientInstructionRow.submittedTime,
                adminReadOrderClientInstructionRow.recordedByUserID, (String) adminReadOrderClientInstructionValues[1],
                adminReadOrderClientInstructionRow.resolvedByAdminID, (String) adminReadOrderClientInstructionValues[2],
                adminReadOrderClientInstructionRow.resolvedTime,
                adminHidePrivateValue(adminReadOrderClientInstructionRow.resolutionNote));
    }

    /**
     * What it does: Builds the read-only shortage row.
     * Input: The saved case and related SKU.
     * Output: The public case fields.
     */
    private AdminViewOrderShortageCaseRow adminReadOrderShortageCase(Object[] adminReadOrderShortageCaseValues) {
        ShortageCase adminReadOrderShortageCaseRow = (ShortageCase) adminReadOrderShortageCaseValues[0];
        return new AdminViewOrderShortageCaseRow(adminReadOrderShortageCaseRow.shortageCaseID,
                adminReadOrderShortageCaseRow.productID, (String) adminReadOrderShortageCaseValues[1],
                adminReadOrderShortageCaseRow.missingQuantity, adminReadOrderShortageCaseRow.caseStatus,
                adminReadOrderShortageCaseRow.decision, adminReadOrderShortageCaseRow.reportedTime,
                adminReadOrderShortageCaseRow.decidedTime);
    }
}
