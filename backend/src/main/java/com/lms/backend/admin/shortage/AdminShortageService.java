package com.lms.backend.admin.shortage;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_CSV_MAXIMUM_ROW_COUNT;
import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCheckReasonText;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.admin.common.AdminPrivateValueRule.adminHidePrivateValue;
import static com.lms.backend.admin.shortage.AdminShortageRule.adminCheckShortageCancelIsAllowed;
import static com.lms.backend.shared.csv.SharedCSVWriter.sharedWriteCSVDownload;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_CSV_DOWNLOADED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_ORDER_CANCELLED_BY_ADMIN;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_REFUND_REQUESTED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_SHORTAGE_CANCEL_DECIDED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_SHORTAGE_WAIT_DECIDED;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_SHORTAGE_CANCELLED;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_SHORTAGE_CANCELLED_REFUND_EXISTS;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_SHORTAGE_CANCELLED_UNPAID;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_SHORTAGE_WAITING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_STATUS_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_STATUS_REJECTED;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.common.AdminDecidedRowResponse;
import com.lms.backend.admin.placeholder.AdminOrderCancellationPlaceholder;
import com.lms.backend.admin.placeholder.AdminRefundPlaceholder;
import com.lms.backend.admin.placeholder.AdminRequestFullRefundAnswer;
import com.lms.backend.shared.database.ClientInstruction;
import com.lms.backend.shared.database.ClientInstructionRepository;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.DeliveryPackage;
import com.lms.backend.shared.database.DeliveryPackageRepository;
import com.lms.backend.shared.database.PickingAssignmentHistoryRepository;
import com.lms.backend.shared.database.PickingTaskRepository;
import com.lms.backend.shared.database.ShortageCase;
import com.lms.backend.shared.database.ShortageCaseRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUser;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.notification.SharedNotificationSender;
import com.lms.backend.shared.notification.SharedNotificationType;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
public class AdminShortageService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final ShortageCaseRepository adminShortageCaseRepository;
    private final CustomerOrderRepository adminShortageOrderRepository;
    private final SharedCurrentUserReader adminShortageCurrentUserReader;
    private final SharedAuditLogWriter adminShortageAuditLogWriter;
    private final SharedNotificationSender adminShortageNotificationSender;
    private final DeliveryPackageRepository adminShortagePackageRepository;
    private final PickingTaskRepository adminShortagePickingTaskRepository;
    private final PickingAssignmentHistoryRepository adminShortageAssignmentRepository;
    private final ClientInstructionRepository adminShortageInstructionRepository;
    private final AdminOrderCancellationPlaceholder adminShortageOrderCancellation;
    private final AdminRefundPlaceholder adminShortageRefund;

    /**
     * What it does: Connects reading and deciding shortage cases to their shared services.
     * Input: The case and order repositories, session reader, audit writer and notification sender.
     * Output: The shortage case service.
     */
    public AdminShortageService(ShortageCaseRepository adminShortageCaseRepository,
            CustomerOrderRepository adminShortageOrderRepository, SharedCurrentUserReader adminShortageCurrentUserReader,
            SharedAuditLogWriter adminShortageAuditLogWriter, SharedNotificationSender adminShortageNotificationSender,
            DeliveryPackageRepository adminShortagePackageRepository,
            PickingTaskRepository adminShortagePickingTaskRepository,
            PickingAssignmentHistoryRepository adminShortageAssignmentRepository,
            ClientInstructionRepository adminShortageInstructionRepository,
            AdminOrderCancellationPlaceholder adminShortageOrderCancellation, AdminRefundPlaceholder adminShortageRefund) {
        this.adminShortageCaseRepository = adminShortageCaseRepository;
        this.adminShortageOrderRepository = adminShortageOrderRepository;
        this.adminShortageCurrentUserReader = adminShortageCurrentUserReader;
        this.adminShortageAuditLogWriter = adminShortageAuditLogWriter;
        this.adminShortageNotificationSender = adminShortageNotificationSender;
        this.adminShortagePackageRepository = adminShortagePackageRepository;
        this.adminShortagePickingTaskRepository = adminShortagePickingTaskRepository;
        this.adminShortageAssignmentRepository = adminShortageAssignmentRepository;
        this.adminShortageInstructionRepository = adminShortageInstructionRepository;
        this.adminShortageOrderCancellation = adminShortageOrderCancellation;
        this.adminShortageRefund = adminShortageRefund;
    }

    // ===== SECTION 2: SHORTAGE CASE SEARCH AND CSV =====

    /**
     * What it does: Lists the shortage report queue oldest first, or the selected history newest first.
     * Input: The optional case filter and one-based page number.
     * Output: The matching page, total count and total pages.
     */
    @Transactional(readOnly = true)
    public AdminSearchShortageCaseResponse adminSearchShortageCase(
            String adminSearchShortageCaseCaseStatus, int adminSearchShortageCasePageNumber) {
        // The combined queue starts with the cases that have waited the longest.
        Sort adminSearchShortageCaseSort = Sort.by(
                "OPEN_AND_WAITING".equals(adminSearchShortageCaseCaseStatus) ? Sort.Direction.ASC : Sort.Direction.DESC,
                "reportedTime", "shortageCaseID");
        int adminSearchShortageCaseRequestedPageNumber = Math.clamp(
                adminSearchShortageCasePageNumber, 1, Integer.MAX_VALUE / ADMIN_TABLE_PAGE_SIZE);
        Page<AdminSearchShortageCaseRow> adminSearchShortageCasePage = adminReadShortageCasePage(
                adminSearchShortageCaseCaseStatus, PageRequest.of(adminSearchShortageCaseRequestedPageNumber - 1,
                        ADMIN_TABLE_PAGE_SIZE, adminSearchShortageCaseSort));

        // If a formerly valid page no longer exists, use the last page.
        long adminSearchShortageCaseTotalRowCount = adminSearchShortageCasePage.getTotalElements();
        int adminSearchShortageCaseTotalPageCount = adminCountPage(adminSearchShortageCaseTotalRowCount);
        int adminSearchShortageCaseActualPageNumber = Math.min(
                adminSearchShortageCaseRequestedPageNumber, adminSearchShortageCaseTotalPageCount);
        if (adminSearchShortageCaseActualPageNumber < adminSearchShortageCaseRequestedPageNumber
                && adminSearchShortageCaseTotalRowCount != 0) {
            adminSearchShortageCasePage = adminReadShortageCasePage(adminSearchShortageCaseCaseStatus,
                    PageRequest.of(adminSearchShortageCaseActualPageNumber - 1,
                            ADMIN_TABLE_PAGE_SIZE, adminSearchShortageCaseSort));
        }
        return new AdminSearchShortageCaseResponse(adminSearchShortageCasePage.getContent(),
                adminSearchShortageCaseActualPageNumber, adminSearchShortageCaseTotalPageCount,
                adminSearchShortageCaseTotalRowCount);
    }

    /**
     * What it does: Exports matching cases and records the download.
     * Input: The optional case filter used by the list.
     * Output: The newest matching cases in a UTF-8 attachment.
     */
    @Transactional
    public ResponseEntity<byte[]> adminDownloadShortageCaseCSV(String adminSearchShortageCaseCaseStatus) {
        // Use the same safe values as the list. The export cap keeps the newest entries.
        Page<AdminSearchShortageCaseRow> adminDownloadShortageCaseCSVPage = adminReadShortageCasePage(
                adminSearchShortageCaseCaseStatus, PageRequest.of(0, ADMIN_CSV_MAXIMUM_ROW_COUNT,
                        Sort.by(Sort.Direction.DESC, "reportedTime", "shortageCaseID")));
        List<List<Object>> adminDownloadShortageCaseCSVRowList = adminDownloadShortageCaseCSVPage.stream()
                .map(adminDownloadShortageCaseCSVRow -> Arrays.<Object>asList(
                        adminDownloadShortageCaseCSVRow.shortageCaseID(),
                        adminDownloadShortageCaseCSVRow.orderID(),
                        adminDownloadShortageCaseCSVRow.adminSearchShortageCaseOrderReference(),
                        adminDownloadShortageCaseCSVRow.adminSearchShortageCaseOrderStatus(),
                        adminDownloadShortageCaseCSVRow.adminSearchShortageCaseOrderIsPaid(),
                        adminDownloadShortageCaseCSVRow.productID(),
                        adminDownloadShortageCaseCSVRow.adminSearchShortageCaseSKU(),
                        adminDownloadShortageCaseCSVRow.adminSearchShortageCaseProductName(),
                        adminDownloadShortageCaseCSVRow.missingQuantity(),
                        adminDownloadShortageCaseCSVRow.adminSearchShortageCaseOrderedQuantity(),
                        adminDownloadShortageCaseCSVRow.reportNote(),
                        adminDownloadShortageCaseCSVRow.reportedByWorkerID(),
                        adminDownloadShortageCaseCSVRow.adminSearchShortageCaseReportedByUsername(),
                        adminDownloadShortageCaseCSVRow.reportedTime(),
                        adminDownloadShortageCaseCSVRow.caseStatus(),
                        adminDownloadShortageCaseCSVRow.decision(),
                        adminDownloadShortageCaseCSVRow.decidedByAdminID(),
                        adminDownloadShortageCaseCSVRow.adminSearchShortageCaseDecidedByUsername(),
                        adminDownloadShortageCaseCSVRow.decidedTime(),
                        adminDownloadShortageCaseCSVRow.decisionNote(),
                        adminDownloadShortageCaseCSVRow.resolvedTime())).toList();

        // Record the active filter and the number of rows sent.
        long actorUserID = adminShortageCurrentUserReader.sharedReadCurrentUser().userID();
        adminShortageAuditLogWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_CSV_DOWNLOADED,
                "SYSTEM", "CSV_SHORTAGE_CASES", null,
                Map.of("rowCount", adminDownloadShortageCaseCSVRowList.size(), "adminSearchShortageCaseCaseStatus",
                        adminSearchShortageCaseCaseStatus == null ? "" : adminSearchShortageCaseCaseStatus));
        return sharedWriteCSVDownload(List.of(
                "shortageCaseID", "orderID", "adminSearchShortageCaseOrderReference",
                "adminSearchShortageCaseOrderStatus", "adminSearchShortageCaseOrderIsPaid", "productID",
                "adminSearchShortageCaseSKU", "adminSearchShortageCaseProductName", "missingQuantity",
                "adminSearchShortageCaseOrderedQuantity", "reportNote", "reportedByWorkerID",
                "adminSearchShortageCaseReportedByUsername", "reportedTime", "caseStatus", "decision",
                "decidedByAdminID", "adminSearchShortageCaseDecidedByUsername", "decidedTime", "decisionNote",
                "resolvedTime"), adminDownloadShortageCaseCSVRowList, "admin-shortage-cases", Instant.now(),
                adminDownloadShortageCaseCSVPage.hasNext() ? "Only the newest 10000 rows are included." : null);
    }

    // ===== SECTION 3: ADMIN SHORTAGE DECISIONS =====

    /**
     * What it does: Authorizes Warehouse to replace a missing or damaged item after restock.
     * Input: The open case ID and the Admin's reason.
     * Output: The confirmation message and saved decision. The case, audit and notification are saved together.
     */
    @Transactional
    public AdminDecidedRowResponse adminDecideShortageWait(long shortageCaseID,
            AdminDecideShortageWaitRequest adminDecideShortageWaitRequest) {
        String adminDecideShortageWaitReason = adminCheckReasonText(
                adminDecideShortageWaitRequest.adminDecideShortageWaitReason());

        // Read the parent ID first, then lock the order and its cases before checking the case state.
        long orderID = adminShortageCaseRepository.findOrderIDByShortageCaseID(shortageCaseID)
                .orElseThrow(AdminShortageService::adminShortageCaseNotFound);
        CustomerOrder adminDecideShortageWaitOrder = adminShortageOrderRepository.findByOrderIDForUpdate(orderID)
                .orElseThrow(AdminShortageService::adminShortageCaseNotFound);
        ShortageCase adminDecideShortageWaitCase = adminShortageCaseRepository.findAllByOrderIDForUpdate(orderID)
                .stream().filter(adminDecideShortageWaitOrderCase ->
                        adminDecideShortageWaitOrderCase.shortageCaseID == shortageCaseID)
                .findFirst().orElseThrow(AdminShortageService::adminShortageCaseNotFound);
        if (!AdminShortageCaseStatus.OPEN.name().equals(adminDecideShortageWaitCase.caseStatus)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_SHORTAGE_CASE_STATE_CONFLICT",
                    "This case was already handled.");
        }
        if (SharedOrderStatus.CANCELLED.name().equals(adminDecideShortageWaitOrder.orderStatus)
                || SharedOrderStatus.DELIVERED.name().equals(adminDecideShortageWaitOrder.orderStatus)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ORDER_STATE_CONFLICT",
                    "The order cannot wait for restock in its current state.");
        }

        // PLACEHOLDER OUTPUT PO-04
        // To: Warehouse.
        // What: The waiting decision permits the assigned worker to replace or repair the affected line.
        // How to use: Keep an OPEN case blocked and permit repair after WAITING_RESTOCK. Nothing reads it yet.
        SharedCurrentUser adminDecideShortageWaitAdmin = adminShortageCurrentUserReader.sharedReadCurrentUser();
        long decidedByAdminID = adminDecideShortageWaitAdmin.userID();
        adminDecideShortageWaitCase.caseStatus = AdminShortageCaseStatus.WAITING_RESTOCK.name();
        adminDecideShortageWaitCase.decision = AdminShortageDecision.WAIT_FOR_RESTOCK.name();
        adminDecideShortageWaitCase.decidedByAdminID = decidedByAdminID;
        adminDecideShortageWaitCase.decidedTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        adminDecideShortageWaitCase.decisionNote = adminDecideShortageWaitReason;

        // Record what actually changed and tell the client that the order is delayed.
        String orderReference = adminDecideShortageWaitOrder.orderReference;
        adminShortageAuditLogWriter.sharedWriteAuditLog(decidedByAdminID, SHARED_AUDIT_ACTION_SHORTAGE_WAIT_DECIDED,
                "SHORTAGE_CASE", Long.toString(shortageCaseID), Map.of("caseStatus", AdminShortageCaseStatus.OPEN.name()),
                Map.of("caseStatus", adminDecideShortageWaitCase.caseStatus, "decision", adminDecideShortageWaitCase.decision,
                        "reason", adminHidePrivateValue(adminDecideShortageWaitReason), "orderReference", orderReference));
        adminShortageNotificationSender.sharedSendNotification(adminDecideShortageWaitOrder.clientUserID, orderID,
                CLIENT_SHORTAGE_WAITING, CLIENT_SHORTAGE_WAITING.sharedFormatNotificationMessage(orderReference));
        return new AdminDecidedRowResponse("The shortage case of order " + orderReference + " now waits for restock.",
                adminDecideShortageWaitCase.caseStatus, null, adminDecideShortageWaitAdmin.username(),
                adminDecideShortageWaitCase.decidedTime, null, false);
    }

    // ===== SECTION 4: adminDecideShortageCancel =====

    /**
     * What it does: Cancels the order and open cases, rejects pending requests and asks for a full refund when paid.
     * Input: The chosen case ID, confirmation of Warehouse stock correction, and the Admin's reason.
     * Output: The saved decision with its refund ID when applicable, or a rule error.
     * Placeholder: PO-01 cancels the order, PO-02 records the refund, and PO-08 tells the client.
     */
    @Transactional
    public AdminDecidedRowResponse adminDecideShortageCancel(long shortageCaseID,
            AdminDecideShortageCancelRequest adminDecideShortageCancelRequest) {
        String adminDecideShortageCancelReason = adminCheckReasonText(
                adminDecideShortageCancelRequest.adminDecideShortageCancelReason());
        if (!Boolean.TRUE.equals(adminDecideShortageCancelRequest.adminDecideShortageCancelStockCorrectionIsConfirmed())) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_STOCK_CORRECTION_NOT_CONFIRMED",
                    "Please confirm that Warehouse has recorded the stock correction for this case.");
        }

        // Locate the order without locking a child row. Then lock the order and its shortage cases in order.
        long orderID = adminShortageCaseRepository.findOrderIDByShortageCaseID(shortageCaseID)
                .orElseThrow(AdminShortageService::adminShortageCaseNotFound);
        CustomerOrder adminDecideShortageCancelOrder = adminShortageOrderRepository.findByOrderIDForUpdate(orderID)
                .orElseThrow(AdminShortageService::adminShortageCaseNotFound);
        List<ShortageCase> adminDecideShortageCancelCaseList = adminShortageCaseRepository.findAllByOrderIDForUpdate(orderID);
        ShortageCase adminDecideShortageCancelCase = adminDecideShortageCancelCaseList.stream()
                .filter(adminDecideShortageCancelCandidate -> adminDecideShortageCancelCandidate.shortageCaseID == shortageCaseID)
                .findFirst().orElseThrow(AdminShortageService::adminShortageCaseNotFound);
        if (!adminCheckShortageCaseCanBeClosed(adminDecideShortageCancelCase)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_SHORTAGE_CASE_STATE_CONFLICT",
                    "This case was already handled.");
        }

        DeliveryPackage adminDecideShortageCancelPackage = adminShortagePackageRepository.findByOrderID(orderID).orElse(null);
        String orderStatus = adminDecideShortageCancelOrder.orderStatus;
        if (!adminCheckShortageCancelIsAllowed(orderStatus, adminDecideShortageCancelPackage != null,
                adminDecideShortageCancelPackage == null ? null : adminDecideShortageCancelPackage.custodyStatus)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ORDER_STATE_CONFLICT",
                    "The order cannot be cancelled in its current state.");
        }

        // Take the picking and client request locks before making changes. Keep the order lock until commit.
        adminShortagePickingTaskRepository.findByOrderIDForUpdate(orderID).ifPresent(adminDecideShortageCancelTask ->
                adminShortageAssignmentRepository.findOpenAssignmentForChange(adminDecideShortageCancelTask.pickingTaskID));
        List<ClientInstruction> adminDecideShortageCancelRequestList = adminShortageInstructionRepository
                .findByOrderIDAndStatusOrderByInstructionIDAsc(orderID, SHARED_INSTRUCTION_STATUS_PENDING);
        SharedCurrentUser adminDecideShortageCancelAdmin = adminShortageCurrentUserReader.sharedReadCurrentUser();
        adminCloseShortageCases(adminDecideShortageCancelCase, adminDecideShortageCancelCaseList,
                adminDecideShortageCancelRequestList, adminDecideShortageCancelAdmin.userID(), adminDecideShortageCancelReason);

        // PLACEHOLDER OUTPUT PO-01. Client and Warehouse will replace this stand-in with shared cancellation.
        adminShortageOrderCancellation.adminCancelOrderPlaceholder(
                orderID, adminDecideShortageCancelAdmin.userID(), adminDecideShortageCancelReason);
        // PLACEHOLDER OUTPUT PO-02. Payment will send the refund and record its actual outcome.
        AdminRequestFullRefundAnswer adminDecideShortageCancelRefund = adminShortageRefund
                .adminRequestFullRefundPlaceholder(orderID, adminDecideShortageCancelAdmin.userID(),
                        adminDecideShortageCancelReason);
        return adminRecordShortageCancellation(adminDecideShortageCancelCase, adminDecideShortageCancelOrder,
                orderStatus, adminDecideShortageCancelAdmin, adminDecideShortageCancelRefund);
    }

    // ===== SECTION 5: HELPER FUNCTIONS =====

    /**
     * What it does: Checks whether the case state permits closing it for cancellation.
     * Input: A locked shortage case.
     * Output: True for an open or waiting case.
     */
    private static boolean adminCheckShortageCaseCanBeClosed(ShortageCase adminCheckShortageCaseCanBeClosedCase) {
        return AdminShortageCaseStatus.OPEN.name().equals(adminCheckShortageCaseCanBeClosedCase.caseStatus)
                || AdminShortageCaseStatus.WAITING_RESTOCK.name().equals(adminCheckShortageCaseCanBeClosedCase.caseStatus);
    }

    /**
     * What it does: Stores the primary shortage decision, closes its peers and rejects pending client requests.
     * Input: The chosen case, its locked peers and requests, and the deciding Admin and reason.
     * Output: The rows are updated in the caller's transaction.
     */
    private static void adminCloseShortageCases(ShortageCase adminCloseShortageCasesChosenCase,
            List<ShortageCase> adminCloseShortageCasesCaseList, List<ClientInstruction> adminCloseShortageCasesRequestList,
            long decidedByAdminID, String adminCloseShortageCasesReason) {
        Instant decidedTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        adminCloseShortageCasesChosenCase.caseStatus = AdminShortageCaseStatus.CANCELLED.name();
        adminCloseShortageCasesChosenCase.decision = AdminShortageDecision.CANCEL_AND_REFUND.name();
        adminCloseShortageCasesChosenCase.decidedByAdminID = decidedByAdminID;
        adminCloseShortageCasesChosenCase.decidedTime = decidedTime;
        adminCloseShortageCasesChosenCase.decisionNote = adminCloseShortageCasesReason;

        // Preserve historical decisions on other cases and explain why their work closed.
        for (ShortageCase adminCloseShortageCasesOtherCase : adminCloseShortageCasesCaseList) {
            if (adminCheckShortageCaseCanBeClosed(adminCloseShortageCasesOtherCase)) {
                adminCloseShortageCasesOtherCase.caseStatus = AdminShortageCaseStatus.CANCELLED.name();
                adminCloseShortageCasesOtherCase.decisionNote = "Closed because the order was cancelled in case "
                        + adminCloseShortageCasesChosenCase.shortageCaseID + ".";
            }
        }

        // PLACEHOLDER INPUT PI-03. Client saves these requests while holding the same order lock.
        for (ClientInstruction adminCloseShortageCasesRequest : adminCloseShortageCasesRequestList) {
            adminCloseShortageCasesRequest.status = SHARED_INSTRUCTION_STATUS_REJECTED;
            adminCloseShortageCasesRequest.resolvedByAdminID = decidedByAdminID;
            adminCloseShortageCasesRequest.resolvedTime = decidedTime;
            adminCloseShortageCasesRequest.resolutionNote = "The order was cancelled because of a stock shortage.";
        }
    }

    /**
     * What it does: Records cancellation audit entries and tells the client what happened to the payment.
     * Input: The saved case and order, the prior order status, the deciding Admin, and the refund result.
     * Output: The message and saved outcome for the Warehouse row.
     */
    private AdminDecidedRowResponse adminRecordShortageCancellation(ShortageCase adminRecordShortageCancellationCase,
            CustomerOrder adminRecordShortageCancellationOrder, String adminRecordShortageCancellationPriorOrderStatus,
            SharedCurrentUser adminRecordShortageCancellationAdmin,
            AdminRequestFullRefundAnswer adminRecordShortageCancellationRefund) {
        Long userID = adminRecordShortageCancellationAdmin.userID();
        String orderReference = adminRecordShortageCancellationOrder.orderReference;
        Long refundID = adminRecordShortageCancellationRefund.refundID();
        boolean adminRequestFullRefundIsNew = adminRecordShortageCancellationRefund.adminRequestFullRefundIsNew();
        String adminRecordShortageCancellationReason =
                adminHidePrivateValue(adminRecordShortageCancellationCase.decisionNote);
        Map<String, Object> adminRecordShortageCancellationAuditValues = new HashMap<>(Map.of(
                "caseStatus", adminRecordShortageCancellationCase.caseStatus,
                "decision", adminRecordShortageCancellationCase.decision,
                "reason", adminRecordShortageCancellationReason,
                "stockCorrectionIsConfirmed", true, "orderReference", orderReference));
        if (refundID != null && !adminRequestFullRefundIsNew) {
            adminRecordShortageCancellationAuditValues.put("existingRefundID", refundID);
        }
        adminShortageAuditLogWriter.sharedWriteAuditLog(userID, SHARED_AUDIT_ACTION_SHORTAGE_CANCEL_DECIDED,
                "SHORTAGE_CASE", adminRecordShortageCancellationCase.shortageCaseID.toString(), null,
                adminRecordShortageCancellationAuditValues);
        adminShortageAuditLogWriter.sharedWriteAuditLog(userID, SHARED_AUDIT_ACTION_ORDER_CANCELLED_BY_ADMIN,
                "ORDER", adminRecordShortageCancellationOrder.orderID.toString(),
                Map.of("orderStatus", adminRecordShortageCancellationPriorOrderStatus),
                Map.of("orderStatus", adminRecordShortageCancellationOrder.orderStatus,
                        "reason", adminRecordShortageCancellationReason));
        if (adminRequestFullRefundIsNew) {
            adminShortageAuditLogWriter.sharedWriteAuditLog(userID, SHARED_AUDIT_ACTION_REFUND_REQUESTED,
                    "ORDER", adminRecordShortageCancellationOrder.orderID.toString(), null, Map.of("refundID", refundID));
        }

        // PLACEHOLDER OUTPUT PO-08. Client pages will read the new order notification.
        SharedNotificationType adminRecordShortageCancellationType;
        String adminRecordShortageCancellationOutcome;
        if (refundID == null) {
            adminRecordShortageCancellationType = CLIENT_SHORTAGE_CANCELLED_UNPAID;
            adminRecordShortageCancellationOutcome = "Nothing had been paid, so no refund was needed.";
        } else if (adminRequestFullRefundIsNew) {
            adminRecordShortageCancellationType = CLIENT_SHORTAGE_CANCELLED;
            adminRecordShortageCancellationOutcome = "A full refund was requested.";
        } else {
            adminRecordShortageCancellationType = CLIENT_SHORTAGE_CANCELLED_REFUND_EXISTS;
            adminRecordShortageCancellationOutcome = "Refund " + refundID
                    + " of this payment already existed, so no new refund was requested.";
        }
        adminShortageNotificationSender.sharedSendNotification(adminRecordShortageCancellationOrder.clientUserID,
                adminRecordShortageCancellationOrder.orderID, adminRecordShortageCancellationType,
                adminRecordShortageCancellationType.sharedFormatNotificationMessage(orderReference));
        String adminRecordShortageCancellationMessage = "Order " + orderReference + " was cancelled. "
                + adminRecordShortageCancellationOutcome;
        return new AdminDecidedRowResponse(adminRecordShortageCancellationMessage,
                adminRecordShortageCancellationCase.caseStatus, null, adminRecordShortageCancellationAdmin.username(),
                adminRecordShortageCancellationCase.decidedTime, refundID, adminRequestFullRefundIsNew);
    }

    // ===== SECTION 6: CASE FILTER AND SAFE ROWS =====

    /**
     * What it does: Selects the cases behind the search and download, then hides private note values.
     * Input: The optional case status and the requested selection, order and limit.
     * Output: A page of the public shortage row records.
     */
    private Page<AdminSearchShortageCaseRow> adminReadShortageCasePage(
            String adminSearchShortageCaseCaseStatus, Pageable adminReadShortageCasePageRequest) {
        // PLACEHOLDER INPUT PI-01
        // From: Warehouse.
        // What: Verified shortage cases stored when a worker pauses the order.
        // How to use: Read the saved cases. Until Warehouse is connected, the test tool creates them through F6.1.
        boolean adminReadShortageCaseAllStatuses =
                adminSearchShortageCaseCaseStatus == null || adminSearchShortageCaseCaseStatus.isBlank();
        List<String> adminReadShortageCaseStatuses;
        if (adminReadShortageCaseAllStatuses) {
            // Keep an IN value even when the all-status flag applies.
            adminReadShortageCaseStatuses = List.of(AdminShortageCaseStatus.OPEN.name());
        } else if ("OPEN_AND_WAITING".equals(adminSearchShortageCaseCaseStatus)) {
            adminReadShortageCaseStatuses =
                    List.of(AdminShortageCaseStatus.OPEN.name(), AdminShortageCaseStatus.WAITING_RESTOCK.name());
        } else {
            try {
                adminReadShortageCaseStatuses =
                        List.of(AdminShortageCaseStatus.valueOf(adminSearchShortageCaseCaseStatus).name());
            } catch (IllegalArgumentException adminReadShortageCaseInvalidStatus) {
                throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID",
                        "Choose a valid shortage case status.");
            }
        }
        return adminShortageCaseRepository.adminSearchShortageCase(
                adminReadShortageCaseAllStatuses, adminReadShortageCaseStatuses, adminReadShortageCasePageRequest)
                .map(AdminShortageService::adminReadShortageCaseRow);
    }

    /**
     * What it does: Puts the joined case facts in the public shape used by the page and the CSV.
     * Input: The case and related order, item, product and staff facts.
     * Output: One case row with its free text checked for private values.
     */
    private static AdminSearchShortageCaseRow adminReadShortageCaseRow(Object[] adminReadShortageCaseRowValues) {
        ShortageCase adminReadShortageCaseRowCase = (ShortageCase) adminReadShortageCaseRowValues[0];
        return new AdminSearchShortageCaseRow(
                adminReadShortageCaseRowCase.shortageCaseID, adminReadShortageCaseRowCase.orderID,
                (String) adminReadShortageCaseRowValues[1], (String) adminReadShortageCaseRowValues[2],
                (boolean) adminReadShortageCaseRowValues[3], adminReadShortageCaseRowCase.productID,
                (String) adminReadShortageCaseRowValues[4], (String) adminReadShortageCaseRowValues[5],
                adminReadShortageCaseRowCase.missingQuantity, (int) adminReadShortageCaseRowValues[6],
                adminHidePrivateValue(adminReadShortageCaseRowCase.reportNote), adminReadShortageCaseRowCase.reportedByWorkerID,
                (String) adminReadShortageCaseRowValues[7], adminReadShortageCaseRowCase.reportedTime,
                adminReadShortageCaseRowCase.caseStatus, adminReadShortageCaseRowCase.decision,
                adminReadShortageCaseRowCase.decidedByAdminID, (String) adminReadShortageCaseRowValues[8],
                adminReadShortageCaseRowCase.decidedTime, adminHidePrivateValue(adminReadShortageCaseRowCase.decisionNote),
                adminReadShortageCaseRowCase.resolvedTime);
    }

    /**
     * What it does: Builds the error used when the requested shortage case cannot be found.
     * Input: None.
     * Output: The public 404 error.
     */
    private static SharedRuleException adminShortageCaseNotFound() {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_SHORTAGE_CASE_NOT_FOUND",
                "The shortage case does not exist.");
    }
}
