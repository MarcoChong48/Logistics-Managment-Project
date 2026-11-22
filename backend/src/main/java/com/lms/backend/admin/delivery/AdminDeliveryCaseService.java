package com.lms.backend.admin.delivery;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_CSV_MAXIMUM_ROW_COUNT;
import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCheckReasonText;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.admin.common.AdminPrivateValueRule.adminHidePrivateValue;
import static com.lms.backend.admin.delivery.AdminDeliveryCaseRule.ADMIN_REMINDER_MINIMUM_HOURS_BETWEEN;
import static com.lms.backend.admin.delivery.AdminDeliveryCaseRule.adminCheckRepeatedFailure;
import static com.lms.backend.admin.delivery.AdminDeliveryCaseRule.adminCountDaysSinceReturn;
import static com.lms.backend.admin.delivery.AdminDeliveryCaseRule.adminFindWaitingFlag;
import static com.lms.backend.shared.csv.SharedCSVWriter.sharedWriteCSVDownload;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_CANCELLATION_APPROVED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_CLIENT_INSTRUCTION_RECORDED_BY_ADMIN;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_CLIENT_INSTRUCTION_REJECTED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_CSV_DOWNLOADED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_DELIVERY_REMINDER_SENT;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_ORDER_CANCELLED_BY_ADMIN;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_REDELIVERY_APPROVED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_REFUND_REQUESTED;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_CANCELLATION_APPROVED;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_CANCELLATION_APPROVED_REFUND_EXISTS;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_CANCELLATION_APPROVED_UNPAID;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_DELIVERY_REMINDER;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_REDELIVERY_APPROVED;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_REQUEST_RECORDED;
import static com.lms.backend.shared.notification.SharedNotificationType.CLIENT_REQUEST_REJECTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_ATTEMPT_OUTCOME_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_EVENT_RETURN_RECEIVED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_WAREHOUSE;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_STATUS_APPROVED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_STATUS_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_STATUS_REJECTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_STATUS_WITHDRAWN;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_TYPE_CANCEL;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_INSTRUCTION_TYPE_REDELIVER;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.common.AdminDecidedRowResponse;
import com.lms.backend.admin.placeholder.AdminOrderCancellationPlaceholder;
import com.lms.backend.admin.placeholder.AdminRefundPlaceholder;
import com.lms.backend.admin.placeholder.AdminRequestFullRefundAnswer;
import com.lms.backend.admin.shortage.AdminShortageCaseStatus;
import com.lms.backend.shared.database.ClientInstruction;
import com.lms.backend.shared.database.ClientInstructionRepository;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.DeliveryAttemptRepository;
import com.lms.backend.shared.database.DeliveryPackage;
import com.lms.backend.shared.database.DeliveryPackageRepository;
import com.lms.backend.shared.database.NotificationRepository;
import com.lms.backend.shared.database.OrderDeliveryDetailRepository;
import com.lms.backend.shared.database.PackageCustodyEvent;
import com.lms.backend.shared.database.PackageCustodyEventRepository;
import com.lms.backend.shared.database.ShortageCaseRepository;
import com.lms.backend.shared.error.SharedMessageResponse;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUser;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.notification.SharedNotificationSender;
import com.lms.backend.shared.notification.SharedNotificationType;
import com.lms.backend.shared.value.SharedInstructionSource;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
public class AdminDeliveryCaseService {

    // ===== SECTION 1: CONSTANTS AND SETTINGS =====

    private final List<String> adminCheckInstructionOpenCaseStatusList = List.of(
            AdminShortageCaseStatus.OPEN.name(), AdminShortageCaseStatus.WAITING_RESTOCK.name());

    private final int adminCheckRepeatedFailureReviewFailureCount;
    private final int adminFindWaitingFlagReminderDays;
    private final int adminFindWaitingFlagFinalReviewDays;

    // ===== SECTION 2: OTHER CLASSES THIS CLASS USES =====

    private final CustomerOrderRepository adminDeliveryCaseOrderRepository;
    private final DeliveryPackageRepository adminDeliveryCasePackageRepository;
    private final DeliveryAttemptRepository adminDeliveryCaseAttemptRepository;
    private final PackageCustodyEventRepository adminDeliveryCaseCustodyRepository;
    private final ClientInstructionRepository adminDeliveryCaseInstructionRepository;
    private final OrderDeliveryDetailRepository adminDeliveryCaseDetailRepository;
    private final ShortageCaseRepository adminDeliveryCaseShortageRepository;
    private final NotificationRepository adminDeliveryCaseNotificationRepository;
    private final SharedCurrentUserReader adminDeliveryCaseCurrentUserReader;
    private final SharedAuditLogWriter adminDeliveryCaseAuditWriter;
    private final SharedNotificationSender adminDeliveryCaseNotificationSender;
    private final AdminOrderCancellationPlaceholder adminDeliveryCaseOrderCancellation;
    private final AdminRefundPlaceholder adminDeliveryCaseRefund;

    /**
     * What it does: Connects failed delivery cases to the database, notification and payment services.
     * Input: The repositories, shared services, placeholders and review settings.
     * Output: A service that can read cases and record Admin decisions.
     */
    public AdminDeliveryCaseService(CustomerOrderRepository adminDeliveryCaseOrderRepository,
            DeliveryPackageRepository adminDeliveryCasePackageRepository,
            DeliveryAttemptRepository adminDeliveryCaseAttemptRepository,
            PackageCustodyEventRepository adminDeliveryCaseCustodyRepository,
            ClientInstructionRepository adminDeliveryCaseInstructionRepository,
            OrderDeliveryDetailRepository adminDeliveryCaseDetailRepository,
            ShortageCaseRepository adminDeliveryCaseShortageRepository,
            NotificationRepository adminDeliveryCaseNotificationRepository,
            SharedCurrentUserReader adminDeliveryCaseCurrentUserReader,
            SharedAuditLogWriter adminDeliveryCaseAuditWriter,
            SharedNotificationSender adminDeliveryCaseNotificationSender,
            AdminOrderCancellationPlaceholder adminDeliveryCaseOrderCancellation,
            AdminRefundPlaceholder adminDeliveryCaseRefund,
            @Value("${lms.admin.review-failure-count}") int adminCheckRepeatedFailureReviewFailureCount,
            @Value("${lms.admin.reminder-days}") int adminFindWaitingFlagReminderDays,
            @Value("${lms.admin.final-review-days}") int adminFindWaitingFlagFinalReviewDays) {
        this.adminDeliveryCaseOrderRepository = adminDeliveryCaseOrderRepository;
        this.adminDeliveryCasePackageRepository = adminDeliveryCasePackageRepository;
        this.adminDeliveryCaseAttemptRepository = adminDeliveryCaseAttemptRepository;
        this.adminDeliveryCaseCustodyRepository = adminDeliveryCaseCustodyRepository;
        this.adminDeliveryCaseInstructionRepository = adminDeliveryCaseInstructionRepository;
        this.adminDeliveryCaseDetailRepository = adminDeliveryCaseDetailRepository;
        this.adminDeliveryCaseShortageRepository = adminDeliveryCaseShortageRepository;
        this.adminDeliveryCaseNotificationRepository = adminDeliveryCaseNotificationRepository;
        this.adminDeliveryCaseCurrentUserReader = adminDeliveryCaseCurrentUserReader;
        this.adminDeliveryCaseAuditWriter = adminDeliveryCaseAuditWriter;
        this.adminDeliveryCaseNotificationSender = adminDeliveryCaseNotificationSender;
        this.adminDeliveryCaseOrderCancellation = adminDeliveryCaseOrderCancellation;
        this.adminDeliveryCaseRefund = adminDeliveryCaseRefund;
        this.adminCheckRepeatedFailureReviewFailureCount = adminCheckRepeatedFailureReviewFailureCount;
        this.adminFindWaitingFlagReminderDays = adminFindWaitingFlagReminderDays;
        this.adminFindWaitingFlagFinalReviewDays = adminFindWaitingFlagFinalReviewDays;
    }

    // ===== SECTION 3: adminSearchFailedDeliveryCase =====

    /**
     * What it does: Filters and pages the failed delivery cases, starting with the oldest failure.
     * Input: The case filter and requested one-based page number.
     * Output: A matching page, its counts and review settings, or an error for an unknown filter.
     * Placeholder: PI-03, PI-04 and PI-08 supply the request, return and attempt facts.
     */
    @Transactional(readOnly = true)
    public AdminSearchFailedDeliveryCaseResponse adminSearchFailedDeliveryCase(
            String adminSearchFailedDeliveryCaseFilter, int adminSearchFailedDeliveryCasePageNumber) {
        List<AdminSearchFailedDeliveryCaseRow> adminSearchFailedDeliveryCaseRowList =
                adminReadFailedDeliveryCases(adminSearchFailedDeliveryCaseFilter, Instant.now());
        int adminSearchFailedDeliveryCaseTotalRowCount = adminSearchFailedDeliveryCaseRowList.size();
        int adminSearchFailedDeliveryCaseTotalPageCount = adminCountPage(adminSearchFailedDeliveryCaseTotalRowCount);
        int adminSearchFailedDeliveryCaseActualPageNumber = Math.clamp(
                adminSearchFailedDeliveryCasePageNumber, 1, adminSearchFailedDeliveryCaseTotalPageCount);
        int adminSearchFailedDeliveryCaseStart = (adminSearchFailedDeliveryCaseActualPageNumber - 1)
                * ADMIN_TABLE_PAGE_SIZE;
        int adminSearchFailedDeliveryCaseEnd = adminSearchFailedDeliveryCaseStart + Math.min(
                ADMIN_TABLE_PAGE_SIZE, adminSearchFailedDeliveryCaseTotalRowCount - adminSearchFailedDeliveryCaseStart);
        return new AdminSearchFailedDeliveryCaseResponse(adminSearchFailedDeliveryCaseRowList.subList(
                adminSearchFailedDeliveryCaseStart, adminSearchFailedDeliveryCaseEnd),
                adminSearchFailedDeliveryCaseActualPageNumber, adminSearchFailedDeliveryCaseTotalPageCount,
                adminSearchFailedDeliveryCaseTotalRowCount, adminCheckRepeatedFailureReviewFailureCount,
                adminFindWaitingFlagReminderDays, adminFindWaitingFlagFinalReviewDays);
    }

    // ===== SECTION 4: adminSendDeliveryReminder =====

    /**
     * What it does: Sends a reminder for a returned order without a request or recent reminder.
     * Input: The failed order's ID.
     * Output: A success message, or the rule that prevents the reminder.
     * Placeholder: PI-03 and PI-04 supply the request and return. PO-08 gives the Client the reminder.
     */
    @Transactional
    public SharedMessageResponse adminSendDeliveryReminder(long orderID) {
        // Lock the order before testing for a request or a competing reminder.
        CustomerOrder adminSendDeliveryReminderOrder = adminLockFailedDeliveryOrder(orderID);
        adminCheckFailedDeliveryOrder(adminSendDeliveryReminderOrder);
        adminCheckReturnConfirmed(adminFindReturnState(adminSendDeliveryReminderOrder));
        // PLACEHOLDER INPUT PI-03
        // From: Client.
        // What: The latest pending client request.
        // How to use: Client writers take the same order lock. Test tools supply the requests locally.
if (adminDeliveryCaseInstructionRepository.existsByOrderIDAndStatus(
                orderID, SHARED_INSTRUCTION_STATUS_PENDING)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT",
                    "The client has already sent a request.");
        }
        Instant adminSendDeliveryReminderLastTime = adminDeliveryCaseNotificationRepository
                .findLatestNotificationTime(orderID, CLIENT_DELIVERY_REMINDER.name()).orElse(null);
        if (adminFindNextReminderTime(adminSendDeliveryReminderLastTime, Instant.now()) != null) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_REMINDER_TOO_SOON",
                    "A reminder was already sent in the last 24 hours.");
        }

        // The saved notification also establishes the next allowed reminder time.
        long actorUserID = adminDeliveryCaseCurrentUserReader.sharedReadCurrentUser().userID();
        String orderReference = adminSendDeliveryReminderOrder.orderReference;
        adminNotifyDeliveryClient(adminSendDeliveryReminderOrder, CLIENT_DELIVERY_REMINDER,
                CLIENT_DELIVERY_REMINDER.sharedFormatNotificationMessage(orderReference));
        adminDeliveryCaseAuditWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_DELIVERY_REMINDER_SENT,
                "ORDER", Long.toString(orderID), null, Map.of("orderReference", orderReference));
        return new SharedMessageResponse("A reminder for order " + orderReference + " was sent to the client.");
    }

    // ===== SECTION 5: adminRecordClientInstruction =====

    /**
     * What it does: Records a manual request, withdrawing the pending request the window showed.
     * Input: The order ID, type, identity check and expected pending instruction ID.
     * Output: The saved decision, or the rule that blocks recording.
     * Placeholder: PI-03 and PI-04 supply pending rows and confirmed returns. PO-08 tells the Client.
     */
    @Transactional
    public AdminDecidedRowResponse adminRecordClientInstruction(long orderID,
            AdminRecordClientInstructionRequest adminRecordClientInstructionRequest) {
        String instructionType = adminRecordClientInstructionRequest.adminRecordClientInstructionType();
        if (!SHARED_INSTRUCTION_TYPE_REDELIVER.equals(instructionType)
                && !SHARED_INSTRUCTION_TYPE_CANCEL.equals(instructionType)) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID",
                    "Choose redelivery or cancellation.");
        }
if (!Boolean.TRUE.equals(
                adminRecordClientInstructionRequest.adminRecordClientInstructionIdentityIsConfirmed())) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_IDENTITY_NOT_CONFIRMED",
                    "Please confirm that you checked the client's identity.");
        }
        String adminRecordClientInstructionIdentityCheckMethod = adminCheckReasonText(
                adminRecordClientInstructionRequest.adminRecordClientInstructionIdentityCheckMethod());

        // Lock the order first, even when this window saw no pending request.
        CustomerOrder adminRecordClientInstructionOrder = adminLockFailedDeliveryOrder(orderID);
        adminCheckFailedDeliveryOrder(adminRecordClientInstructionOrder);
        AdminReturnState adminRecordClientInstructionReturn = adminFindReturnState(adminRecordClientInstructionOrder);
        adminCheckReturnConfirmed(adminRecordClientInstructionReturn);
        // PLACEHOLDER INPUT PI-03
        // From: Client.
        // What: Pending requests created from the tracking page or recorded earlier by Admin.
        // How to use: Client writers take the order lock before updating these rows.
        List<ClientInstruction> adminRecordClientInstructionPendingList = adminDeliveryCaseInstructionRepository
.findByOrderIDAndStatusOrderBySubmittedTimeDescInstructionIDDesc(
                        orderID, SHARED_INSTRUCTION_STATUS_PENDING);
        Long adminRecordClientInstructionPendingID = adminRecordClientInstructionPendingList.isEmpty()
                ? null : adminRecordClientInstructionPendingList.getFirst().instructionID;
        if (!Objects.equals(adminRecordClientInstructionPendingID,
                adminRecordClientInstructionRequest.adminRecordClientInstructionExpectedPendingInstructionID())) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT",
                    "The pending request changed while this window was open. Refresh the request and check it again.");
        }

        // Bind the request to the stored details version and to the Warehouse receipt.
        long deliveryDetailID = adminDeliveryCaseDetailRepository.findLatestDeliveryDetailID(orderID)
                .orElseThrow(() -> new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ORDER_STATE_CONFLICT",
                        "The order has no saved delivery details."));
SharedCurrentUser adminRecordClientInstructionAdmin =
                adminDeliveryCaseCurrentUserReader.sharedReadCurrentUser();
        ClientInstruction adminRecordClientInstructionSaved = adminSaveRecordedInstruction(
                adminRecordClientInstructionOrder, instructionType, deliveryDetailID,
                adminRecordClientInstructionReturn.adminFindReturnStateReturnCustodyEventID(),
                adminRecordClientInstructionAdmin, adminRecordClientInstructionPendingList);
        return adminLogRecordedInstruction(adminRecordClientInstructionOrder, adminRecordClientInstructionSaved,
                adminRecordClientInstructionAdmin, adminRecordClientInstructionIdentityCheckMethod);
    }

    // ===== SECTION 6: adminApproveRedelivery =====

    /**
     * What it does: Approves the current redelivery request for a returned, unblocked order.
     * Input: The instruction ID and optional note.
     * Output: The saved decision, or the rule that blocks redelivery.
     * Placeholder: PI-03 and PI-04 supply the request and return. PO-06 and PO-08 hand off the result.
     */
    @Transactional
    public AdminDecidedRowResponse adminApproveRedelivery(long instructionID,
            AdminApproveRedeliveryRequest adminApproveRedeliveryRequest) {
        String adminApproveRedeliveryNote = adminCheckApprovalNote(adminApproveRedeliveryRequest == null
                ? null : adminApproveRedeliveryRequest.adminApproveRedeliveryNote());
        CustomerOrder adminApproveRedeliveryOrder = adminLockInstructionOrder(instructionID);
        ClientInstruction adminApproveRedeliveryInstruction =
                adminLockPendingInstruction(instructionID, adminApproveRedeliveryOrder);
        adminCheckInstructionCanBeApproved(adminApproveRedeliveryOrder, adminApproveRedeliveryInstruction,
                SHARED_INSTRUCTION_TYPE_REDELIVER);

        // PLACEHOLDER OUTPUT PO-06
        // To: Warehouse, Delivery and Client.
        // What: The approved request and order ready for redelivery.
// How to use: Warehouse schedules pickup. Delivery uses the details version and Client locks that version.
        SharedCurrentUser adminApproveRedeliveryAdmin = adminDeliveryCaseCurrentUserReader.sharedReadCurrentUser();
        adminResolveClientInstruction(adminApproveRedeliveryInstruction, SHARED_INSTRUCTION_STATUS_APPROVED,
adminApproveRedeliveryAdmin.userID(), Instant.now().truncatedTo(ChronoUnit.MICROS),
                adminApproveRedeliveryNote);
        adminApproveRedeliveryOrder.orderStatus = SharedOrderStatus.READY_FOR_REDELIVERY.name();

        // Capture the approved version ID, without reading its contact details.
        Map<String, Object> adminApproveRedeliveryAuditValues = new LinkedHashMap<>();
        adminApproveRedeliveryAuditValues.put("status", SHARED_INSTRUCTION_STATUS_APPROVED);
        adminApproveRedeliveryAuditValues.put("orderReference", adminApproveRedeliveryOrder.orderReference);
        adminApproveRedeliveryAuditValues.put("deliveryDetailID", adminApproveRedeliveryInstruction.deliveryDetailID);
        adminApproveRedeliveryAuditValues.put("note", adminHidePrivateValue(adminApproveRedeliveryNote));
        adminDeliveryCaseAuditWriter.sharedWriteAuditLog(adminApproveRedeliveryAdmin.userID(),
                SHARED_AUDIT_ACTION_REDELIVERY_APPROVED, "CLIENT_INSTRUCTION", Long.toString(instructionID),
                Map.of("status", SHARED_INSTRUCTION_STATUS_PENDING), adminApproveRedeliveryAuditValues);
        adminNotifyDeliveryClient(adminApproveRedeliveryOrder, CLIENT_REDELIVERY_APPROVED,
                CLIENT_REDELIVERY_APPROVED.sharedFormatNotificationMessage(adminApproveRedeliveryOrder.orderReference));
        return new AdminDecidedRowResponse(
                "Redelivery for order " + adminApproveRedeliveryOrder.orderReference + " was approved.",
                adminApproveRedeliveryOrder.orderStatus, null, adminApproveRedeliveryAdmin.username(),
                adminApproveRedeliveryInstruction.resolvedTime, null, false);
    }

    // ===== SECTION 7: adminApproveCancellation =====

    /**
     * What it does: Approves the client's request, cancels the order and requests any applicable refund.
     * Input: The instruction ID and optional note.
     * Output: The saved decision and refund ID when applicable, or the rule that blocks cancellation.
     * Placeholder: PI-03 and PI-04 supply the request and return. PO-01, PO-02 and PO-08 hand off the results.
     */
    @Transactional
    public AdminDecidedRowResponse adminApproveCancellation(long instructionID,
            AdminApproveCancellationRequest adminApproveCancellationRequest) {
        String adminApproveCancellationNote = adminCheckApprovalNote(adminApproveCancellationRequest == null
                ? null : adminApproveCancellationRequest.adminApproveCancellationNote());
        CustomerOrder adminApproveCancellationOrder = adminLockInstructionOrder(instructionID);
        ClientInstruction adminApproveCancellationInstruction =
                adminLockPendingInstruction(instructionID, adminApproveCancellationOrder);
        adminCheckInstructionCanBeApproved(adminApproveCancellationOrder, adminApproveCancellationInstruction,
                SHARED_INSTRUCTION_TYPE_CANCEL);
        SharedCurrentUser adminApproveCancellationAdmin = adminDeliveryCaseCurrentUserReader.sharedReadCurrentUser();
        adminResolveClientInstruction(adminApproveCancellationInstruction, SHARED_INSTRUCTION_STATUS_APPROVED,
                adminApproveCancellationAdmin.userID(), Instant.now().truncatedTo(ChronoUnit.MICROS),
                adminApproveCancellationNote);

        // PLACEHOLDER OUTPUT PO-01
        // To: Client and Warehouse.
        // What: Final cancellation and release of reserved stock.
        // How to use: Replace the local stand-in with the shared order cancellation function.
        long orderID = adminApproveCancellationOrder.orderID;
        String adminApproveCancellationReason = adminApproveCancellationNote.isEmpty()
                ? "Client cancellation request approved." : adminHidePrivateValue(adminApproveCancellationNote);
        adminDeliveryCaseOrderCancellation.adminCancelOrderPlaceholder(
                orderID, adminApproveCancellationAdmin.userID(), adminApproveCancellationReason);
        // PLACEHOLDER OUTPUT PO-02
        // To: Payment.
        // What: The refund outcome for the latest positive accepted payment, when there is one.
        // How to use: Connect the provider to request a refund when none blocks it and settle other payments.
        AdminRequestFullRefundAnswer adminApproveCancellationRefund = adminDeliveryCaseRefund
                .adminRequestFullRefundPlaceholder(
                        orderID, adminApproveCancellationAdmin.userID(), adminApproveCancellationReason);
        return adminLogApprovedCancellation(adminApproveCancellationOrder, adminApproveCancellationInstruction,
                adminApproveCancellationAdmin, adminApproveCancellationRefund);
    }

    // ===== SECTION 8: adminRejectClientInstruction =====

    /**
     * What it does: Rejects a pending instruction and lets the client send another.
     * Input: The instruction ID and rejection reason.
     * Output: The saved decision, or a rule error if the request cannot be rejected.
     * Placeholder: PI-03 and PI-04 supply the request and return. PO-08 gives the Client the reason.
     */
    @Transactional
    public AdminDecidedRowResponse adminRejectClientInstruction(long instructionID,
            AdminRejectClientInstructionRequest adminRejectClientInstructionRequest) {
        String adminRejectClientInstructionReason =
                adminCheckReasonText(adminRejectClientInstructionRequest.adminRejectClientInstructionReason());
        CustomerOrder adminRejectClientInstructionOrder = adminLockInstructionOrder(instructionID);
        ClientInstruction adminRejectClientInstructionSaved =
                adminLockPendingInstruction(instructionID, adminRejectClientInstructionOrder);
SharedCurrentUser adminRejectClientInstructionAdmin =
                adminDeliveryCaseCurrentUserReader.sharedReadCurrentUser();
        adminResolveClientInstruction(adminRejectClientInstructionSaved, SHARED_INSTRUCTION_STATUS_REJECTED,
                adminRejectClientInstructionAdmin.userID(), Instant.now().truncatedTo(ChronoUnit.MICROS),
                adminRejectClientInstructionReason);

        // Use the saved wording for the client and omit private numbers from exported or delivered text.
        String orderReference = adminRejectClientInstructionOrder.orderReference;
        String adminRejectClientInstructionPublicReason = adminHidePrivateValue(adminRejectClientInstructionReason);
        adminDeliveryCaseAuditWriter.sharedWriteAuditLog(adminRejectClientInstructionAdmin.userID(),
                SHARED_AUDIT_ACTION_CLIENT_INSTRUCTION_REJECTED, "CLIENT_INSTRUCTION", Long.toString(instructionID),
Map.of("status", SHARED_INSTRUCTION_STATUS_PENDING), Map.of(
                        "status", SHARED_INSTRUCTION_STATUS_REJECTED,
                        "reason", adminRejectClientInstructionPublicReason,
                        "orderReference", orderReference));
        adminNotifyDeliveryClient(adminRejectClientInstructionOrder, CLIENT_REQUEST_REJECTED,
                CLIENT_REQUEST_REJECTED.sharedFormatNotificationMessage(orderReference)
                        .replace("{reason}", adminRejectClientInstructionPublicReason));
        String adminRejectClientInstructionState = adminFindReturnState(adminRejectClientInstructionOrder)
                .adminFindReturnStateIsReturned() ? "NO_CLIENT_REQUEST" : "WAITING_FOR_RETURN";
        return new AdminDecidedRowResponse("The request for order " + orderReference + " was rejected.",
                adminRejectClientInstructionState, null, adminRejectClientInstructionAdmin.username(),
                adminRejectClientInstructionSaved.resolvedTime, null, false);
    }

    // ===== SECTION 9: adminDownloadFailedDeliveryCaseCSV =====

    /**
     * What it does: Exports the current failed delivery filter and audits the download.
     * Input: The case filter.
     * Output: The newest matching rows as a CSV attachment, capped at 10,000.
     * Placeholder: PI-03, PI-04 and PI-08 supply the same public facts as the case list.
     */
    @Transactional
    public ResponseEntity<byte[]> adminDownloadFailedDeliveryCaseCSV(String adminSearchFailedDeliveryCaseFilter) {
        Instant adminDownloadFailedDeliveryCaseCSVTime = Instant.now();
        List<AdminSearchFailedDeliveryCaseRow> adminDownloadFailedDeliveryCaseCSVMatches =
adminReadFailedDeliveryCases(
                        adminSearchFailedDeliveryCaseFilter, adminDownloadFailedDeliveryCaseCSVTime);
List<List<Object>> adminDownloadFailedDeliveryCaseCSVRowList =
                adminDownloadFailedDeliveryCaseCSVMatches.stream().sorted(Comparator.comparing(
                        AdminSearchFailedDeliveryCaseRow::adminSearchFailedDeliveryCaseLatestFailureTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdminSearchFailedDeliveryCaseRow::orderID, Comparator.reverseOrder()))
                .limit(ADMIN_CSV_MAXIMUM_ROW_COUNT)
                .map(AdminDeliveryCaseService::adminBuildFailedDeliveryCaseCSVRow).toList();

        long actorUserID = adminDeliveryCaseCurrentUserReader.sharedReadCurrentUser().userID();
        adminDeliveryCaseAuditWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_CSV_DOWNLOADED,
                "SYSTEM", "CSV_FAILED_DELIVERY_CASES", null,
                Map.of("rowCount", adminDownloadFailedDeliveryCaseCSVRowList.size(),
                        "adminSearchFailedDeliveryCaseFilter", adminSearchFailedDeliveryCaseFilter));
        return sharedWriteCSVDownload(List.of("orderID", "orderReference", "adminSearchFailedDeliveryCaseFailureCount",
                "adminSearchFailedDeliveryCaseLatestFailureReason", "adminSearchFailedDeliveryCaseLatestFailureTime",
                "adminSearchFailedDeliveryCaseLatestInternalNote", "adminSearchFailedDeliveryCaseLatestWorkerUsername",
                "adminSearchFailedDeliveryCaseIsReturned", "adminSearchFailedDeliveryCaseReturnedTime",
                "adminSearchFailedDeliveryCaseDaysSinceReturn", "adminSearchFailedDeliveryCasePendingInstructionID",
"adminSearchFailedDeliveryCasePendingInstructionType",
                "adminSearchFailedDeliveryCasePendingInstructionSource",
                "adminSearchFailedDeliveryCasePendingInstructionSubmittedTime",
                "adminSearchFailedDeliveryCaseIsRepeatedFailure",
                "adminSearchFailedDeliveryCaseWaitingFlag", "adminSearchFailedDeliveryCaseLastReminderTime",
                "adminSearchFailedDeliveryCaseNextReminderTime", "adminSearchFailedDeliveryCaseHasOpenShortageCase",
                "adminSearchFailedDeliveryCaseOrderIsPaid"), adminDownloadFailedDeliveryCaseCSVRowList,
                "admin-failed-delivery-cases", adminDownloadFailedDeliveryCaseCSVTime,
                adminDownloadFailedDeliveryCaseCSVMatches.size() > ADMIN_CSV_MAXIMUM_ROW_COUNT
                        ? "Only the newest 10000 rows are included." : null);
    }

    // ===== SECTION 10: CASE SEARCH, RETURN AND REMINDER HELPERS =====

    /**
     * What it does: Reads failed delivery cases, selects the requested filter and leaves their order intact.
     * Input: The requested filter and the clock time shared by all rows.
     * Output: The matching public rows in oldest-failure-first order, or 400 for an unknown filter.
     * Placeholder: PI-03, PI-04 and PI-08 provide the saved case facts.
     */
    @Transactional(readOnly = true)
    public List<AdminSearchFailedDeliveryCaseRow> adminReadFailedDeliveryCases(
            String adminSearchFailedDeliveryCaseFilter, Instant adminReadFailedDeliveryCasesTime) {
        if (!Set.of("ALL", "WAITING_FOR_RETURN", "NEEDS_DECISION", "NO_CLIENT_REQUEST", "FLAGGED")
                .contains(adminSearchFailedDeliveryCaseFilter)) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID", "Choose a valid case filter.");
        }

        // PLACEHOLDER INPUT PI-03, PI-04 and PI-08
        // From: Client (requests), Warehouse (return receipts), Payment (accepted payments) and Delivery (attempts).
        // What: Public facts of failed orders and their current requests or custody.
        // How to use: Role owners write the shared rows. The test tools supply local examples.
        return adminDeliveryCaseOrderRepository.adminSearchFailedDeliveryCase(SharedOrderStatus.DELIVERY_FAILED.name(),
                SHARED_ATTEMPT_OUTCOME_FAILED, SHARED_CUSTODY_STATUS_WAREHOUSE, SHARED_CUSTODY_EVENT_RETURN_RECEIVED,
                SHARED_INSTRUCTION_STATUS_PENDING, CLIENT_DELIVERY_REMINDER.name(),
                adminCheckInstructionOpenCaseStatusList).stream()
                .map(adminReadFailedDeliveryCasesFacts -> adminBuildFailedDeliveryCaseRow(
                        adminReadFailedDeliveryCasesFacts, adminReadFailedDeliveryCasesTime))
                .filter(adminReadFailedDeliveryCasesRow -> adminCheckFailedDeliveryCaseFilter(
                        adminReadFailedDeliveryCasesRow, adminSearchFailedDeliveryCaseFilter))
                .toList();
    }

    /**
     * What it does: Adds the flags and reminder time and masks the worker's free-text note.
     * Input: The joined public order facts and current time.
     * Output: One failed delivery case in the API and CSV shape.
     */
    private AdminSearchFailedDeliveryCaseRow adminBuildFailedDeliveryCaseRow(
            Object[] adminBuildFailedDeliveryCaseRowFacts, Instant adminBuildFailedDeliveryCaseRowTime) {
        long adminSearchFailedDeliveryCaseFailureCount = (Long) adminBuildFailedDeliveryCaseRowFacts[2];
        Instant adminSearchFailedDeliveryCaseReturnedTime = (Instant) adminBuildFailedDeliveryCaseRowFacts[7];
        Long adminSearchFailedDeliveryCasePendingInstructionID = (Long) adminBuildFailedDeliveryCaseRowFacts[8];
        Instant adminSearchFailedDeliveryCaseLastReminderTime = (Instant) adminBuildFailedDeliveryCaseRowFacts[12];
        boolean adminSearchFailedDeliveryCaseIsReturned = adminSearchFailedDeliveryCaseReturnedTime != null;
        long adminSearchFailedDeliveryCaseDaysSinceReturn = adminSearchFailedDeliveryCaseIsReturned
? adminCountDaysSinceReturn(
                        adminSearchFailedDeliveryCaseReturnedTime, adminBuildFailedDeliveryCaseRowTime)
                : 0;
        AdminReviewWaitingFlag adminSearchFailedDeliveryCaseWaitingFlag = adminFindWaitingFlag(
                adminSearchFailedDeliveryCaseIsReturned, adminSearchFailedDeliveryCasePendingInstructionID != null,
adminSearchFailedDeliveryCaseDaysSinceReturn, adminFindWaitingFlagReminderDays,
                adminFindWaitingFlagFinalReviewDays);

        return new AdminSearchFailedDeliveryCaseRow(
                (Long) adminBuildFailedDeliveryCaseRowFacts[0], (String) adminBuildFailedDeliveryCaseRowFacts[1],
                adminSearchFailedDeliveryCaseFailureCount, (String) adminBuildFailedDeliveryCaseRowFacts[3],
                (Instant) adminBuildFailedDeliveryCaseRowFacts[4],
                adminHidePrivateValue((String) adminBuildFailedDeliveryCaseRowFacts[5]),
                (String) adminBuildFailedDeliveryCaseRowFacts[6], adminSearchFailedDeliveryCaseIsReturned,
                adminSearchFailedDeliveryCaseReturnedTime,
                adminSearchFailedDeliveryCaseIsReturned ? adminSearchFailedDeliveryCaseDaysSinceReturn : null,
                adminSearchFailedDeliveryCasePendingInstructionID, (String) adminBuildFailedDeliveryCaseRowFacts[9],
                (String) adminBuildFailedDeliveryCaseRowFacts[10], (Instant) adminBuildFailedDeliveryCaseRowFacts[11],
adminCheckRepeatedFailure(
                        adminSearchFailedDeliveryCaseFailureCount, adminCheckRepeatedFailureReviewFailureCount),
                adminSearchFailedDeliveryCaseWaitingFlag, adminSearchFailedDeliveryCaseLastReminderTime,
adminFindNextReminderTime(
                        adminSearchFailedDeliveryCaseLastReminderTime, adminBuildFailedDeliveryCaseRowTime),
                (Boolean) adminBuildFailedDeliveryCaseRowFacts[13], (Boolean) adminBuildFailedDeliveryCaseRowFacts[14]);
    }

    /**
     * What it does: Checks the selected filter against a case's saved facts and computed flags.
     * Input: A case row and one valid filter.
     * Output: True when the row matches.
     */
    public static boolean adminCheckFailedDeliveryCaseFilter(
            AdminSearchFailedDeliveryCaseRow adminCheckCaseFilterRow,
            String adminSearchFailedDeliveryCaseFilter) {
        return switch (adminSearchFailedDeliveryCaseFilter) {
            case "ALL" -> true;
            case "WAITING_FOR_RETURN" -> !adminCheckCaseFilterRow.adminSearchFailedDeliveryCaseIsReturned();
            case "NEEDS_DECISION" ->
                    adminCheckCaseFilterRow.adminSearchFailedDeliveryCasePendingInstructionID() != null;
            case "NO_CLIENT_REQUEST" -> adminCheckCaseFilterRow.adminSearchFailedDeliveryCaseIsReturned()
                    && adminCheckCaseFilterRow.adminSearchFailedDeliveryCasePendingInstructionID() == null;
            case "FLAGGED" -> adminCheckCaseFilterRow.adminSearchFailedDeliveryCaseIsRepeatedFailure()
                    || adminCheckCaseFilterRow.adminSearchFailedDeliveryCaseWaitingFlag()
                            != AdminReviewWaitingFlag.NONE;
            default -> false;
        };
    }

    /**
     * What it does: Checks whether Warehouse received the package during the latest failure cycle.
     * Input: The order whose current package and latest failed attempt must be checked.
     * Output: The receipt ID and time when returned, and the newest failure time when known.
     * Placeholder: PI-04 supplies the receipt and custody state.
     */
    private AdminReturnState adminFindReturnState(CustomerOrder adminFindReturnStateOrder) {
        DeliveryPackage adminFindReturnStatePackage = adminDeliveryCasePackageRepository
                .findByOrderID(adminFindReturnStateOrder.orderID).orElse(null);
        if (adminFindReturnStatePackage == null) {
            return new AdminReturnState(false, null, null, null);
        }
        Instant adminFindReturnStateLatestFailureTime = adminDeliveryCaseAttemptRepository
                .findFirstByPackageIDAndOutcomeAndEndTimeIsNotNullOrderByEndTimeDescDeliveryAttemptIDDesc(
                        adminFindReturnStatePackage.packageID, SHARED_ATTEMPT_OUTCOME_FAILED)
                .map(adminFindReturnStateAttempt -> adminFindReturnStateAttempt.endTime).orElse(null);

        // PLACEHOLDER INPUT PI-04
        // From: Warehouse.
        // What: Warehouse custody and the return receipt for this failure cycle.
        // How to use: Warehouse sets the custody status when saving the receipt. Tests supply these facts locally.
        if (adminFindReturnStateLatestFailureTime == null
                || !SHARED_CUSTODY_STATUS_WAREHOUSE.equals(adminFindReturnStatePackage.custodyStatus)) {
            return new AdminReturnState(false, null, null, adminFindReturnStateLatestFailureTime);
        }
        PackageCustodyEvent adminFindReturnStateReceipt = adminDeliveryCaseCustodyRepository
                .findLatestReturnReceipt(adminFindReturnStatePackage.packageID, SHARED_CUSTODY_EVENT_RETURN_RECEIVED,
                        adminFindReturnStateLatestFailureTime).orElse(null);
        if (adminFindReturnStateReceipt == null) {
            return new AdminReturnState(false, null, null, adminFindReturnStateLatestFailureTime);
        }
        return new AdminReturnState(true, adminFindReturnStateReceipt.eventTime,
                adminFindReturnStateReceipt.custodyEventID, adminFindReturnStateLatestFailureTime);
    }

    /**
     * What it does: Works out when the 24-hour reminder block will end, if still in effect.
     * Input: The newest reminder's saved time, or empty, and the current time.
     * Output: The end of the block, or empty when sending is allowed.
     */
    private static Instant adminFindNextReminderTime(Instant adminFindNextReminderTimeLastTime,
            Instant adminFindNextReminderTimeNow) {
        if (adminFindNextReminderTimeLastTime == null) {
            return null;
        }
        Instant adminFindNextReminderTimeAllowedTime =
                adminFindNextReminderTimeLastTime.plus(ADMIN_REMINDER_MINIMUM_HOURS_BETWEEN, ChronoUnit.HOURS);
        return adminFindNextReminderTimeAllowedTime.isAfter(adminFindNextReminderTimeNow)
                ? adminFindNextReminderTimeAllowedTime : null;
    }

    /**
     * What it does: Writes the same public fields and order as the CSV header.
     * Input: A failed delivery case.
     * Output: Its ordered CSV fields, including empty optional values.
     */
    private static List<Object> adminBuildFailedDeliveryCaseCSVRow(
            AdminSearchFailedDeliveryCaseRow adminBuildFailedDeliveryCaseCSVRowCase) {
        return Arrays.asList(
                adminBuildFailedDeliveryCaseCSVRowCase.orderID(),
                adminBuildFailedDeliveryCaseCSVRowCase.orderReference(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseFailureCount(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseLatestFailureReason(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseLatestFailureTime(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseLatestInternalNote(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseLatestWorkerUsername(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseIsReturned(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseReturnedTime(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseDaysSinceReturn(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCasePendingInstructionID(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCasePendingInstructionType(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCasePendingInstructionSource(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCasePendingInstructionSubmittedTime(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseIsRepeatedFailure(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseWaitingFlag(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseLastReminderTime(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseNextReminderTime(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseHasOpenShortageCase(),
                adminBuildFailedDeliveryCaseCSVRowCase.adminSearchFailedDeliveryCaseOrderIsPaid());
    }

    // ===== SECTION 11: ORDER AND REQUEST LOCKS =====

    /**
     * What it does: Locks the parent order before a reminder or a client request decision checks its state.
     * Input: The order ID.
     * Output: The locked order, or 404 when it does not exist.
     */
    private CustomerOrder adminLockFailedDeliveryOrder(long orderID) {
        return adminDeliveryCaseOrderRepository.findByOrderIDForUpdate(orderID)
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_ORDER_NOT_FOUND",
                        "The order does not exist."));
    }

    /**
     * What it does: Finds the request's parent and locks the order before locking the request.
     * Input: The instruction ID.
     * Output: Its locked order, or 404 when the instruction is missing.
     * Placeholder: PI-03 supplies client requests and uses the same lock order.
     */
    private CustomerOrder adminLockInstructionOrder(long instructionID) {
        // PLACEHOLDER INPUT PI-03
        // From: Client.
        // What: The order ID for a request.
        // How to use: Client saves the request after locking the order. Admin uses the same lock order.
        long orderID = adminDeliveryCaseInstructionRepository.findOrderIDByInstructionID(instructionID)
                .orElseThrow(AdminDeliveryCaseService::adminClientInstructionNotFound);
        return adminLockFailedDeliveryOrder(orderID);
    }

    /**
     * What it does: Checks the current request state under a lock while its parent order is locked.
     * Input: The request ID and already locked order.
     * Output: The locked pending request, or a 404 or 409 rule error.
     */
private ClientInstruction adminLockPendingInstruction(
            long instructionID, CustomerOrder adminLockPendingInstructionOrder) {
        ClientInstruction adminLockPendingInstructionSaved = adminDeliveryCaseInstructionRepository
                .findByInstructionIDAndOrderID(instructionID, adminLockPendingInstructionOrder.orderID)
                .orElseThrow(AdminDeliveryCaseService::adminClientInstructionNotFound);
        if (!SHARED_INSTRUCTION_STATUS_PENDING.equals(adminLockPendingInstructionSaved.status)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT",
                    "This request was already handled or replaced. Please refresh the page.");
        }
        return adminLockPendingInstructionSaved;
    }

    /**
     * What it does: Checks the requested action against the newest request, the order and the return.
     * Input: The locked order and pending instruction, and the expected request type.
     * Output: None, or the first rule error that blocks approval.
     */
    private void adminCheckInstructionCanBeApproved(CustomerOrder adminCheckInstructionOrder,
            ClientInstruction adminCheckInstructionSaved, String adminCheckInstructionType) {
        if (!adminCheckInstructionType.equals(adminCheckInstructionSaved.instructionType)) {
            String adminCheckInstructionWrongType = SHARED_INSTRUCTION_TYPE_REDELIVER.equals(adminCheckInstructionType)
                    ? "This is not a redelivery request." : "This is not a cancellation request.";
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT",
                    adminCheckInstructionWrongType);
        }
        boolean adminCheckInstructionIsNewest = adminDeliveryCaseInstructionRepository
                .findFirstByOrderIDOrderBySubmittedTimeDescInstructionIDDesc(adminCheckInstructionOrder.orderID)
                .map(adminCheckInstructionLatest -> adminCheckInstructionLatest.instructionID
                        .equals(adminCheckInstructionSaved.instructionID)).orElse(false);
        if (!adminCheckInstructionIsNewest) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT",
                    "This request was already handled or replaced. Please refresh the page.");
        }

        adminCheckFailedDeliveryOrder(adminCheckInstructionOrder);
        AdminReturnState adminCheckInstructionReturnState = adminFindReturnState(adminCheckInstructionOrder);
        adminCheckReturnConfirmed(adminCheckInstructionReturnState);
        if (!Objects.equals(adminCheckInstructionSaved.returnCustodyEventID,
                adminCheckInstructionReturnState.adminFindReturnStateReturnCustodyEventID())) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT",
                    "This request belongs to an earlier return of the package, so it cannot be approved. "
                            + "Reject it or record a new request.");
        }
        if (adminDeliveryCaseShortageRepository.existsByOrderIDAndCaseStatusIn(
                adminCheckInstructionOrder.orderID, adminCheckInstructionOpenCaseStatusList)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_OPEN_SHORTAGE_CASE_BLOCKS",
                    "Decide the open shortage case of this order first.");
        }
    }

    /**
     * What it does: Requires the current order to still be a failed delivery.
     * Input: The locked order.
     * Output: None, or 409 when the order has a different state.
     */
    private static void adminCheckFailedDeliveryOrder(CustomerOrder adminCheckFailedDeliveryOrderSaved) {
        if (!SharedOrderStatus.DELIVERY_FAILED.name().equals(adminCheckFailedDeliveryOrderSaved.orderStatus)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ORDER_STATE_CONFLICT",
                    "The order is no longer a failed delivery.");
        }
    }

    /**
     * What it does: Requires verified return to Warehouse for reminders, recording and approvals.
     * Input: The return state for the order's latest failure.
     * Output: None, or 409 when Warehouse has not confirmed the return.
     */
    private static void adminCheckReturnConfirmed(AdminReturnState adminCheckReturnConfirmedState) {
        if (!adminCheckReturnConfirmedState.adminFindReturnStateIsReturned()) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_RETURN_NOT_CONFIRMED",
                    "The package has not been confirmed as returned yet.");
        }
    }

    /**
     * What it does: Builds the shared missing-request error.
     * Input: None.
     * Output: The public 404 rule error.
     */
    private static SharedRuleException adminClientInstructionNotFound() {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_CLIENT_INSTRUCTION_NOT_FOUND",
                "The client request does not exist.");
    }

    /**
     * What it does: Trims an optional approval note and checks that it fits the saved note field.
     * Input: The note, which may be missing.
     * Output: The trimmed note, or 400 for more than 300 characters.
     */
    private static String adminCheckApprovalNote(String adminCheckApprovalNoteText) {
String adminCheckApprovalNoteTrimmed =
                adminCheckApprovalNoteText == null ? "" : adminCheckApprovalNoteText.strip();
        if (adminCheckApprovalNoteTrimmed.codePointCount(0, adminCheckApprovalNoteTrimmed.length()) > 300) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID",
                    "The note can have at most 300 characters.");
        }
        return adminCheckApprovalNoteTrimmed;
    }

    // ===== SECTION 12: DECISION WRITES AND NOTIFICATIONS =====

    /**
     * What it does: Saves the decision details on the managed instruction row.
     * Input: The locked request, new status, Admin ID, decision time and note.
     * Output: The fields are updated in the caller's transaction.
     */
    private static void adminResolveClientInstruction(ClientInstruction adminResolveInstructionSaved, String status,
            long resolvedByAdminID, Instant resolvedTime, String resolutionNote) {
        adminResolveInstructionSaved.status = status;
        adminResolveInstructionSaved.resolvedByAdminID = resolvedByAdminID;
        adminResolveInstructionSaved.resolvedTime = resolvedTime;
        adminResolveInstructionSaved.resolutionNote = resolutionNote;
    }

    /**
     * What it does: Withdraws the locked pending requests and saves the replacement from Admin.
     * Input: The order and its locked requests, request type, details version, return event and recording Admin.
     * Output: The new pending request with its generated ID.
     */
    private ClientInstruction adminSaveRecordedInstruction(CustomerOrder adminSaveRecordedInstructionOrder,
String instructionType, long deliveryDetailID, long returnCustodyEventID,
            SharedCurrentUser adminSaveInstructionAdmin,
            List<ClientInstruction> adminSaveRecordedInstructionPendingList) {
        Instant submittedTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (ClientInstruction adminSaveRecordedInstructionEarlier : adminSaveRecordedInstructionPendingList) {
            adminResolveClientInstruction(adminSaveRecordedInstructionEarlier, SHARED_INSTRUCTION_STATUS_WITHDRAWN,
                    adminSaveInstructionAdmin.userID(), submittedTime, "Replaced by a request recorded by Admin.");
        }

        ClientInstruction adminSaveRecordedInstructionRequest = new ClientInstruction();
        adminSaveRecordedInstructionRequest.orderID = adminSaveRecordedInstructionOrder.orderID;
        adminSaveRecordedInstructionRequest.clientUserID = adminSaveRecordedInstructionOrder.clientUserID;
        adminSaveRecordedInstructionRequest.instructionType = instructionType;
        adminSaveRecordedInstructionRequest.status = SHARED_INSTRUCTION_STATUS_PENDING;
        adminSaveRecordedInstructionRequest.submittedTime = submittedTime;
        adminSaveRecordedInstructionRequest.source = SharedInstructionSource.ADMIN_MANUAL.name();
        adminSaveRecordedInstructionRequest.recordedByUserID = adminSaveInstructionAdmin.userID();
        adminSaveRecordedInstructionRequest.deliveryDetailID = deliveryDetailID;
        adminSaveRecordedInstructionRequest.returnCustodyEventID = returnCustodyEventID;
        return adminDeliveryCaseInstructionRepository.save(adminSaveRecordedInstructionRequest);
    }

    /**
     * What it does: Audits the manual request, tells the client and returns the saved decision.
     * Input: The order, new request, recording Admin and identity-check method.
     * Output: The success message and submission time.
     * Placeholder: PO-08 gives the Client a notification.
     */
    private AdminDecidedRowResponse adminLogRecordedInstruction(CustomerOrder adminLogRecordedInstructionOrder,
            ClientInstruction adminLogRecordedInstructionSaved, SharedCurrentUser adminLogRecordedInstructionAdmin,
            String adminLogRecordedInstructionCheckMethod) {
        String orderReference = adminLogRecordedInstructionOrder.orderReference;
        String instructionType = adminLogRecordedInstructionSaved.instructionType;
        adminDeliveryCaseAuditWriter.sharedWriteAuditLog(adminLogRecordedInstructionAdmin.userID(),
                SHARED_AUDIT_ACTION_CLIENT_INSTRUCTION_RECORDED_BY_ADMIN,
                "CLIENT_INSTRUCTION", adminLogRecordedInstructionSaved.instructionID.toString(), null,
                Map.of("instructionType", instructionType, "source", adminLogRecordedInstructionSaved.source,
                        "identityCheckMethod", adminHidePrivateValue(adminLogRecordedInstructionCheckMethod),
                        "orderReference", orderReference));
        String adminLogRecordedInstructionTypeText =
                SHARED_INSTRUCTION_TYPE_REDELIVER.equals(instructionType) ? "redelivery" : "cancellation";
        adminNotifyDeliveryClient(adminLogRecordedInstructionOrder, CLIENT_REQUEST_RECORDED,
                CLIENT_REQUEST_RECORDED.sharedFormatNotificationMessage(orderReference)
                        .replace("{requestType}", adminLogRecordedInstructionTypeText));
        return new AdminDecidedRowResponse("A " + adminLogRecordedInstructionTypeText + " request for order "
+ orderReference + " was recorded.", "NEEDS_DECISION", null,
                adminLogRecordedInstructionAdmin.username(), adminLogRecordedInstructionSaved.submittedTime,
                null, false);
    }

    /**
     * What it does: Audits the decision and sends the cancellation message for the saved refund outcome.
     * Input: The cancelled order, approved request, deciding Admin and the full-refund result.
     * Output: The cancellation outcome displayed on the decided row.
     * Placeholder: PO-08 gives the Client a notification.
     */
    private AdminDecidedRowResponse adminLogApprovedCancellation(CustomerOrder adminLogApprovedCancellationOrder,
            ClientInstruction adminLogApprovedCancellationRequest, SharedCurrentUser adminLogApprovedCancellationAdmin,
            AdminRequestFullRefundAnswer adminLogApprovedCancellationRefund) {
        long actorUserID = adminLogApprovedCancellationAdmin.userID();
        String orderReference = adminLogApprovedCancellationOrder.orderReference;
        Long refundID = adminLogApprovedCancellationRefund.refundID();
        boolean adminLogApprovedCancellationRefundIsNew =
                adminLogApprovedCancellationRefund.adminRequestFullRefundIsNew();

        // The cancellation audit distinguishes an existing refund from a newly requested one.
        Map<String, Object> adminLogApprovedCancellationAuditValues = new LinkedHashMap<>();
        adminLogApprovedCancellationAuditValues.put("status", SHARED_INSTRUCTION_STATUS_APPROVED);
        adminLogApprovedCancellationAuditValues.put("orderReference", orderReference);
        adminLogApprovedCancellationAuditValues.put(
                "note", adminHidePrivateValue(adminLogApprovedCancellationRequest.resolutionNote));
        if (refundID != null && !adminLogApprovedCancellationRefundIsNew) {
            adminLogApprovedCancellationAuditValues.put("existingRefundID", refundID);
        }
        adminDeliveryCaseAuditWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_CANCELLATION_APPROVED,
                "CLIENT_INSTRUCTION", adminLogApprovedCancellationRequest.instructionID.toString(),
                Map.of("status", SHARED_INSTRUCTION_STATUS_PENDING), adminLogApprovedCancellationAuditValues);
        adminDeliveryCaseAuditWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_ORDER_CANCELLED_BY_ADMIN,
                "ORDER", adminLogApprovedCancellationOrder.orderID.toString(),
                Map.of("orderStatus", SharedOrderStatus.DELIVERY_FAILED.name()),
                Map.of("orderStatus", SharedOrderStatus.CANCELLED.name(), "orderReference", orderReference));
        if (adminLogApprovedCancellationRefundIsNew) {
            adminDeliveryCaseAuditWriter.sharedWriteAuditLog(actorUserID, SHARED_AUDIT_ACTION_REFUND_REQUESTED,
                    "ORDER", adminLogApprovedCancellationOrder.orderID.toString(), null, Map.of("refundID", refundID));
        }

        // Match the client notification and the administrator's message to the same refund result.
        SharedNotificationType adminLogApprovedCancellationType;
        String adminLogApprovedCancellationRefundMessage;
        if (adminLogApprovedCancellationRefundIsNew) {
            adminLogApprovedCancellationType = CLIENT_CANCELLATION_APPROVED;
            adminLogApprovedCancellationRefundMessage = "A full refund was requested.";
        } else if (refundID != null) {
            adminLogApprovedCancellationType = CLIENT_CANCELLATION_APPROVED_REFUND_EXISTS;
            adminLogApprovedCancellationRefundMessage = "Refund " + refundID
                    + " of this payment already existed, so no new refund was requested.";
        } else {
            adminLogApprovedCancellationType = CLIENT_CANCELLATION_APPROVED_UNPAID;
            adminLogApprovedCancellationRefundMessage = "Nothing had been paid, so no refund was needed.";
        }
        adminNotifyDeliveryClient(adminLogApprovedCancellationOrder, adminLogApprovedCancellationType,
                adminLogApprovedCancellationType.sharedFormatNotificationMessage(orderReference));
        return new AdminDecidedRowResponse(
                "Order " + orderReference + " was cancelled. " + adminLogApprovedCancellationRefundMessage,
                SharedOrderStatus.CANCELLED.name(),
                null, adminLogApprovedCancellationAdmin.username(),
                adminLogApprovedCancellationRequest.resolvedTime, refundID, adminLogApprovedCancellationRefundIsNew);
    }

    /**
     * What it does: Saves a client notification in the same transaction as the decision.
     * Input: The order, the notification type and the public message.
     * Output: One unread notification addressed to the order's client.
     * Placeholder: PO-08 gives the Client the unread row.
     */
    private void adminNotifyDeliveryClient(CustomerOrder adminNotifyDeliveryClientOrder,
            SharedNotificationType adminNotifyDeliveryClientType, String message) {
        // PLACEHOLDER OUTPUT PO-08
        // To: Client.
        // What: The public message about the failed delivery or request.
        // How to use: The Client module displays unread rows for the signed-in client and marks them read.
        adminDeliveryCaseNotificationSender.sharedSendNotification(adminNotifyDeliveryClientOrder.clientUserID,
                adminNotifyDeliveryClientOrder.orderID, adminNotifyDeliveryClientType, message);
    }
}
