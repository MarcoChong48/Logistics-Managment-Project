package com.lms.backend.admin.refund;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_CSV_MAXIMUM_ROW_COUNT;
import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCheckReasonText;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.admin.common.AdminPrivateValueRule.adminHidePrivateValue;
import static com.lms.backend.admin.refund.AdminRefundRule.adminCheckRefundBlocksRequest;
import static com.lms.backend.admin.refund.AdminRefundRule.adminCheckRefundIsUnresolved;
import static com.lms.backend.admin.refund.AdminRefundRule.adminCountRefundAgeHours;
import static com.lms.backend.shared.csv.SharedCSVWriter.sharedWriteCSVDownload;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_CSV_DOWNLOADED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_REFUND_RETRIED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_REFUND_STATUS_CHECKED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_REFUNDED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_REQUESTED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.admin.common.AdminDecidedRowResponse;
import com.lms.backend.admin.placeholder.AdminRefundPlaceholder;
import com.lms.backend.admin.placeholder.AdminRefundProviderAnswer;
import com.lms.backend.admin.placeholder.AdminRefundProviderOutcome;
import com.lms.backend.shared.database.AuditLog;
import com.lms.backend.shared.database.AuditLogRepository;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.Payment;
import com.lms.backend.shared.database.PaymentRepository;
import com.lms.backend.shared.database.Refund;
import com.lms.backend.shared.database.RefundRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUser;
import com.lms.backend.shared.login.SharedCurrentUserReader;

import jakarta.persistence.EntityManager;

@Service
public class AdminRefundService {

    // ===== SECTION 1: CONSTANTS =====

    // ===== SECTION 2: OTHER CLASSES THIS CLASS USES =====

    private final RefundRepository adminSearchRefundRepository;
    private final PaymentRepository adminCheckRefundStatusPaymentRepository;
    private final CustomerOrderRepository adminCheckRefundStatusOrderRepository;
    private final AuditLogRepository adminCheckRefundStatusAuditRepository;
    private final SharedAuditLogWriter adminCheckRefundStatusAuditWriter;
    private final SharedCurrentUserReader adminCheckRefundStatusCurrentUserReader;
    private final EntityManager adminCheckRefundStatusEntityManager;
    private final TransactionTemplate adminCheckRefundStatusTransaction;
    private final AdminRefundPlaceholder adminCheckRefundStatusPlaceholder;

    /**
     * What it does: Connects refund actions to their tables, the signed-in Admin and Payment.
     * Input: The shared repositories, audit writer, user reader, transaction helpers and refund placeholder.
     * Output: A service that can commit a provider check before starting a retry.
     */
    public AdminRefundService(RefundRepository adminSearchRefundRepository,
            PaymentRepository adminCheckRefundStatusPaymentRepository,
            CustomerOrderRepository adminCheckRefundStatusOrderRepository,
            AuditLogRepository adminCheckRefundStatusAuditRepository,
            SharedAuditLogWriter adminCheckRefundStatusAuditWriter,
            SharedCurrentUserReader adminCheckRefundStatusCurrentUserReader,
            EntityManager adminCheckRefundStatusEntityManager,
            PlatformTransactionManager adminCheckRefundStatusTransactionManager,
            AdminRefundPlaceholder adminCheckRefundStatusPlaceholder) {
        this.adminSearchRefundRepository = adminSearchRefundRepository;
        this.adminCheckRefundStatusPaymentRepository = adminCheckRefundStatusPaymentRepository;
        this.adminCheckRefundStatusOrderRepository = adminCheckRefundStatusOrderRepository;
        this.adminCheckRefundStatusAuditRepository = adminCheckRefundStatusAuditRepository;
        this.adminCheckRefundStatusAuditWriter = adminCheckRefundStatusAuditWriter;
        this.adminCheckRefundStatusCurrentUserReader = adminCheckRefundStatusCurrentUserReader;
        this.adminCheckRefundStatusEntityManager = adminCheckRefundStatusEntityManager;
        this.adminCheckRefundStatusPlaceholder = adminCheckRefundStatusPlaceholder;

        // Each execution finishes independently, including when a later retry is refused.
        this.adminCheckRefundStatusTransaction = new TransactionTemplate(adminCheckRefundStatusTransactionManager);
        this.adminCheckRefundStatusTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ===== SECTION 3: adminSearchRefund =====

    /**
     * What it does: Finds the refund history rows for the requested page, oldest first.
     * Input: Whether to include only open cases and the requested page number.
     * Output: The refund rows of that page and the matching counts.
     * Placeholder: PI-08. Payment and the stand-in save the rows in the shared refund table.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchRefundResponse adminSearchRefund(boolean adminSearchRefundIsUnresolvedOnly,
            int adminSearchRefundPageNumber) {
        int adminSearchRefundRequestedPageNumber =
                Math.clamp(adminSearchRefundPageNumber, 1, Integer.MAX_VALUE / ADMIN_TABLE_PAGE_SIZE);
        Page<AdminSearchRefundRow> adminSearchRefundPage = adminReadRefundPage(adminSearchRefundIsUnresolvedOnly,
                PageRequest.of(adminSearchRefundRequestedPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
        long adminSearchRefundTotalRowCount = adminSearchRefundPage.getTotalElements();
        int adminSearchRefundTotalPageCount = adminCountPage(adminSearchRefundTotalRowCount);
        int adminSearchRefundActualPageNumber = Math.min(adminSearchRefundRequestedPageNumber,
                adminSearchRefundTotalPageCount);

        // Keep navigation valid when the number of unresolved rows has fallen between requests.
        if (adminSearchRefundActualPageNumber < adminSearchRefundRequestedPageNumber
                && adminSearchRefundTotalRowCount > 0) {
            adminSearchRefundPage = adminReadRefundPage(adminSearchRefundIsUnresolvedOnly,
                    PageRequest.of(adminSearchRefundActualPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
        }
        return new AdminSearchRefundResponse(adminSearchRefundPage.getContent(), adminSearchRefundActualPageNumber,
                adminSearchRefundTotalPageCount, adminSearchRefundTotalRowCount);
    }

    // ===== SECTION 4: adminCheckRefundStatus =====

    /**
     * What it does: Records one provider check and commits it before returning.
     * Input: The requested refund ID.
     * Output: The provider outcome and stored status, or 404 for a missing refund.
     * Placeholder: PI-07. Payment supplies the result of the check.
     */
    public AdminCheckRefundStatusResponse adminCheckRefundStatus(Long refundID) {
        SharedCurrentUser adminCheckRefundStatusActor = adminCheckRefundStatusCurrentUserReader.sharedReadCurrentUser();
        return adminCheckRefundStatusTransaction.execute(adminCheckRefundStatusTransactionStatus ->
                adminCheckRefundStatus(refundID, adminCheckRefundStatusActor));
    }

    /**
     * What it does: Checks a pending attempt under its payment lock and records the outcome.
     * Input: The refund ID and signed-in Admin.
     * Output: The status and provider message. A completed refund returns its existing outcome.
     * Placeholder: PI-07. The stand-in reads its test result, or reports that no provider is connected.
     */
    private AdminCheckRefundStatusResponse adminCheckRefundStatus(Long refundID,
            SharedCurrentUser adminCheckRefundStatusActor) {
        Refund adminCheckRefundStatusRefund = adminFindRefund(refundID);
        if (SHARED_REFUND_STATUS_REFUNDED.equals(adminCheckRefundStatusRefund.refundStatus)) {
            return adminReadCompletedRefundStatus(refundID);
        }

        // Read again under the payment lock in case another Admin completed the refund first.
        Payment adminCheckRefundStatusPayment = adminLockRefundPayment(adminCheckRefundStatusRefund);
        if (SHARED_REFUND_STATUS_REFUNDED.equals(adminCheckRefundStatusRefund.refundStatus)) {
            return adminReadCompletedRefundStatus(refundID);
        }

        // PLACEHOLDER INPUT PI-07.
        // From: Payment.
        // What: The provider's recorded outcome and its public explanation.
        // How to use: Ask the real provider. For now use the stand-in answer for this refund.
        AdminRefundProviderAnswer adminCheckRefundStatusAnswer =
                adminCheckRefundStatusPlaceholder.adminCheckRefundProviderStatusPlaceholder(refundID);
        AdminRefundProviderOutcome adminCheckRefundStatusOutcome =
                adminCheckRefundStatusAnswer.adminCheckRefundProviderStatusOutcome();
        String adminCheckRefundStatusOldWord = adminCheckRefundStatusRefund.refundStatus;

        // An unknown answer is recorded without changing even a previously failed status.
        switch (adminCheckRefundStatusOutcome) {
            case REFUNDED -> {
                adminCheckRefundStatusRefund.refundStatus = SHARED_REFUND_STATUS_REFUNDED;
                adminCheckRefundStatusRefund.completedTime = Instant.now();
            }
            case FAILED -> adminCheckRefundStatusRefund.refundStatus = SHARED_REFUND_STATUS_FAILED;
            case PENDING -> adminCheckRefundStatusRefund.refundStatus = SHARED_REFUND_STATUS_PENDING;
            case UNKNOWN -> {
            }
        }
        String orderReference = adminReadRefundOrderReference(adminCheckRefundStatusPayment);
        adminCheckRefundStatusAuditWriter.sharedWriteAuditLog(adminCheckRefundStatusActor.userID(),
                SHARED_AUDIT_ACTION_REFUND_STATUS_CHECKED, "REFUND", refundID.toString(),
                Map.of("refundStatus", adminCheckRefundStatusOldWord),
                Map.of("refundStatus", adminCheckRefundStatusRefund.refundStatus, "providerOutcome",
                        adminCheckRefundStatusOutcome.name(), "orderReference", orderReference));
        return new AdminCheckRefundStatusResponse(refundID, adminCheckRefundStatusRefund.refundStatus,
                adminCheckRefundStatusOutcome, adminBuildRefundProviderMessage(adminCheckRefundStatusAnswer));
    }

    // ===== SECTION 5: adminRetryRefund =====

    /**
     * What it does: Checks the reason, saves a fresh provider check, and proceeds only after a confirmed failure.
     * Input: The refund ID and the required reason.
     * Output: The new refund decision, or a shared rule error. A refusal keeps the result of the provider check.
     * Placeholder: PI-07 supplies the check that can authorize a retry. PO-03 creates the new request.
     */
    public AdminDecidedRowResponse adminRetryRefund(Long refundID, AdminRetryRefundRequest adminRetryRefundRequest) {
        String adminRetryRefundReason = adminCheckReasonText(adminRetryRefundRequest.adminRetryRefundReason());
        AdminRefundProviderOutcome adminRetryRefundOutcome =
                adminCheckRefundStatus(refundID).adminCheckRefundStatusProviderOutcome();
        if (adminRetryRefundOutcome != AdminRefundProviderOutcome.FAILED) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_REFUND_STATE_CONFLICT",
                    "The provider reports " + adminRetryRefundOutcome.name()
                            + ". A new try is only allowed after a confirmed failure.");
        }

        // The check's transaction has already released the payment lock.
        SharedCurrentUser adminRetryRefundActor = adminCheckRefundStatusCurrentUserReader.sharedReadCurrentUser();
        return adminCheckRefundStatusTransaction.execute(adminRetryRefundTransactionStatus ->
                adminRetryRefund(refundID, adminRetryRefundReason, adminRetryRefundActor));
    }

    /**
     * What it does: Checks all attempts under the payment lock and requests one new refund.
     * Input: The old refund ID, checked reason and signed-in Admin.
     * Output: The saved decision or 409 if another check or attempt blocks the refund.
     * Placeholder: PO-03. The stand-in only saves a new refund row.
     */
    private AdminDecidedRowResponse adminRetryRefund(Long refundID, String adminRetryRefundReason,
            SharedCurrentUser adminRetryRefundActor) {
        Refund adminRetryRefundOldRefund = adminFindRefund(refundID);
        Payment adminRetryRefundPayment = adminLockRefundPayment(adminRetryRefundOldRefund);
        AuditLog adminRetryRefundLastCheck = adminReadLastRefundCheck(refundID);
        if (adminRetryRefundLastCheck == null
                || adminCheckRefundBlocksRequest(adminRetryRefundOldRefund.refundStatus, adminRetryRefundLastCheck)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_REFUND_STATE_CONFLICT",
                    "The refund status changed. Please refresh the page.");
        }

        // Every other attempt must also be failed without a contradictory provider result.
        for (Refund adminRetryRefundAttempt : adminSearchRefundRepository
                .findByPaymentIDOrderByCreatedTimeDescRefundIDDesc(adminRetryRefundPayment.paymentID)) {
            if (refundID.equals(adminRetryRefundAttempt.refundID)) {
                continue;
            }
            if (!SHARED_REFUND_STATUS_FAILED.equals(adminRetryRefundAttempt.refundStatus)
                    || adminCheckRefundBlocksRequest(adminRetryRefundAttempt.refundStatus,
                            adminReadLastRefundCheck(adminRetryRefundAttempt.refundID))) {
                throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_REFUND_STATE_CONFLICT",
                        "Another refund for this payment already exists.");
            }
        }

        // PLACEHOLDER OUTPUT PO-03.
        // To: Payment.
        // What: A new request for the same amount after a confirmed failure.
        // How to use: Send the provider the new request. For now only save its local row.
        Long adminRetryRefundNewRefundID = adminCheckRefundStatusPlaceholder.adminRetryRefundPlaceholder(
                refundID, adminRetryRefundActor.userID(), adminRetryRefundReason);
        Refund adminRetryRefundNewRefund = adminSearchRefundRepository.findById(adminRetryRefundNewRefundID)
                .orElseThrow();
        String orderReference = adminReadRefundOrderReference(adminRetryRefundPayment);
        adminCheckRefundStatusAuditWriter.sharedWriteAuditLog(adminRetryRefundActor.userID(),
                SHARED_AUDIT_ACTION_REFUND_RETRIED, "REFUND", refundID.toString(), null,
                Map.of("newRefundID", adminRetryRefundNewRefundID, "reason", adminRetryRefundReason,
                        "orderReference", orderReference));
        return new AdminDecidedRowResponse("A new refund was requested for order " + orderReference + ".",
                SHARED_REFUND_STATUS_REQUESTED, null, adminRetryRefundActor.username(),
                adminRetryRefundNewRefund.createdTime, adminRetryRefundNewRefundID, true);
    }

    // ===== SECTION 6: adminDownloadRefundCSV =====

    /**
     * What it does: Exports the same filtered and ordered data as the refund list and records the download.
     * Input: Whether to include only unresolved rows.
     * Output: A UTF-8 CSV attachment with the first 10,000 matching rows.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> adminDownloadRefundCSV(boolean adminSearchRefundIsUnresolvedOnly) {
        Page<AdminSearchRefundRow> adminDownloadRefundCSVPage =
                adminReadRefundPage(adminSearchRefundIsUnresolvedOnly, PageRequest.of(0, ADMIN_CSV_MAXIMUM_ROW_COUNT));
        List<List<Object>> adminDownloadRefundCSVRowList = adminDownloadRefundCSVPage.stream()
                .map(adminDownloadRefundCSVRow -> Arrays.<Object>asList(
                        adminDownloadRefundCSVRow.refundID(), adminDownloadRefundCSVRow.paymentID(),
                        adminDownloadRefundCSVRow.adminSearchRefundOrderID(),
                        adminDownloadRefundCSVRow.adminSearchRefundOrderReference(),
                        adminDownloadRefundCSVRow.adminSearchRefundOrderStatus(), adminDownloadRefundCSVRow.amount(),
                        adminDownloadRefundCSVRow.refundStatus(), adminDownloadRefundCSVRow.createdTime(),
                        adminDownloadRefundCSVRow.completedTime(),
                        adminDownloadRefundCSVRow.adminSearchRefundAgeHours(),
                        adminDownloadRefundCSVRow.adminSearchRefundIsUnresolved(),
                        adminDownloadRefundCSVRow.adminSearchRefundLastCheckTime(),
                        adminDownloadRefundCSVRow.adminSearchRefundLastCheckOutcome(),
                        adminDownloadRefundCSVRow.adminSearchRefundReplacedByRefundID())).toList();

        // Audit only the chosen filter and the number of rows in the file.
        adminCheckRefundStatusAuditWriter.sharedWriteAuditLog(
                adminCheckRefundStatusCurrentUserReader.sharedReadCurrentUser().userID(),
                SHARED_AUDIT_ACTION_CSV_DOWNLOADED, "SYSTEM", "CSV_REFUNDS", null,
                Map.of("rowCount", adminDownloadRefundCSVRowList.size(),
                        "filters", Map.of("adminSearchRefundIsUnresolvedOnly", adminSearchRefundIsUnresolvedOnly)));
        String adminDownloadRefundCSVLimitMessage =
                adminDownloadRefundCSVPage.getTotalElements() > ADMIN_CSV_MAXIMUM_ROW_COUNT
                        ? "Only the oldest 10000 rows are included." : null;
        return sharedWriteCSVDownload(List.of("refundID", "paymentID", "adminSearchRefundOrderID",
                "adminSearchRefundOrderReference", "adminSearchRefundOrderStatus", "amount", "refundStatus",
                "createdTime", "completedTime", "adminSearchRefundAgeHours", "adminSearchRefundIsUnresolved",
                "adminSearchRefundLastCheckTime", "adminSearchRefundLastCheckOutcome",
                "adminSearchRefundReplacedByRefundID"), adminDownloadRefundCSVRowList, "admin-refunds", Instant.now(),
                adminDownloadRefundCSVLimitMessage);
    }

    // ===== SECTION 7: HELPER FUNCTIONS =====

    /**
     * What it does: Builds the public refund rows for a page or export.
     * Input: The unresolved filter and page offset and size.
     * Output: The refund rows for that range and the matching count.
     * Placeholder: PI-08. The shared table contains rows written by Payment and the stand-in.
     */
    private Page<AdminSearchRefundRow> adminReadRefundPage(boolean adminSearchRefundIsUnresolvedOnly,
            PageRequest adminReadRefundPageRequest) {
        // PLACEHOLDER INPUT PI-08.
        // From: Payment.
        // What: The refund attempts and their associated payments and orders.
        // How to use: Read the shared tables. The current rows come from test tools and the refund stand-in.
        Instant adminReadRefundPageCurrentTime = Instant.now();
        return adminSearchRefundRepository.adminSearchRefund(adminSearchRefundIsUnresolvedOnly,
                SHARED_REFUND_STATUS_REFUNDED, SHARED_AUDIT_ACTION_REFUND_STATUS_CHECKED, adminReadRefundPageRequest)
                .map(adminReadRefundPageRow ->
                        adminBuildRefundRow(adminReadRefundPageRow, adminReadRefundPageCurrentTime));
    }

    /**
     * What it does: Adds age and open-case state to the selected refund fields.
     * Input: One repository row and the shared time for the current page or export.
     * Output: The public refund row.
     */
    private AdminSearchRefundRow adminBuildRefundRow(Object[] adminBuildRefundRowValues,
            Instant adminBuildRefundRowCurrentTime) {
        String refundStatus = (String) adminBuildRefundRowValues[6];
        Instant createdTime = (Instant) adminBuildRefundRowValues[7];
        Instant completedTime = (Instant) adminBuildRefundRowValues[8];
        Long adminSearchRefundReplacedByRefundID = (Long) adminBuildRefundRowValues[11];
        Instant adminBuildRefundRowEndTime = SHARED_REFUND_STATUS_REFUNDED.equals(refundStatus) && completedTime != null
                ? completedTime : adminBuildRefundRowCurrentTime;
        return new AdminSearchRefundRow(
                (Long) adminBuildRefundRowValues[0], (Long) adminBuildRefundRowValues[1],
                (Long) adminBuildRefundRowValues[2], (String) adminBuildRefundRowValues[3],
                (String) adminBuildRefundRowValues[4], (BigDecimal) adminBuildRefundRowValues[5],
                refundStatus, createdTime, completedTime,
                adminCountRefundAgeHours(createdTime, adminBuildRefundRowEndTime),
                adminCheckRefundIsUnresolved(refundStatus, adminSearchRefundReplacedByRefundID),
                (Instant) adminBuildRefundRowValues[9], (String) adminBuildRefundRowValues[10],
                adminSearchRefundReplacedByRefundID);
    }

    /**
     * What it does: Finds an attempt or gives a shared missing-refund error.
     * Input: The refund ID.
     * Output: The refund, or 404 when there is no matching row.
     */
    private Refund adminFindRefund(Long refundID) {
        return adminSearchRefundRepository.findById(refundID)
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_REFUND_NOT_FOUND",
                        "No refund was found with this number."));
    }

    /**
     * What it does: Takes the payment lock used by full refunds and refreshes the attempt under it.
     * Input: A refund first read in the caller's transaction.
     * Output: Its locked payment. The given refund now holds the latest state.
     */
    private Payment adminLockRefundPayment(Refund adminLockRefundPaymentRefund) {
        Payment adminLockRefundPaymentRow = adminCheckRefundStatusPaymentRepository
                .findPaymentByPaymentIDForUpdate(adminLockRefundPaymentRefund.paymentID).orElseThrow();
        adminCheckRefundStatusEntityManager.refresh(adminLockRefundPaymentRefund);
        return adminLockRefundPaymentRow;
    }

    /**
     * What it does: Reads the public order reference without taking another database lock.
     * Input: The payment for this refund.
     * Output: The order reference.
     */
    private String adminReadRefundOrderReference(Payment adminReadRefundOrderReferencePayment) {
        return adminCheckRefundStatusOrderRepository.findById(adminReadRefundOrderReferencePayment.orderID)
                .orElseThrow().orderReference;
    }

    /**
     * What it does: Reads the newest provider result saved for the requested attempt.
     * Input: The refund ID.
     * Output: The provider check row, or null when no check exists.
     */
    private AuditLog adminReadLastRefundCheck(Long refundID) {
        return adminCheckRefundStatusAuditRepository
                .findFirstByActionAndTargetTypeAndTargetIDOrderByTimestampDescAuditIDDesc(
                        SHARED_AUDIT_ACTION_REFUND_STATUS_CHECKED, "REFUND", refundID.toString())
                .orElse(null);
    }

    /**
     * What it does: Reports an already completed refund without recording a new provider check.
     * Input: The completed refund ID.
     * Output: The existing refunded status and completion message.
     */
    private AdminCheckRefundStatusResponse adminReadCompletedRefundStatus(Long refundID) {
        return new AdminCheckRefundStatusResponse(refundID, SHARED_REFUND_STATUS_REFUNDED,
                AdminRefundProviderOutcome.REFUNDED, "This refund is already completed.");
    }

    /**
     * What it does: Formats the provider outcome and an optional public explanation for the page.
     * Input: The provider answer.
     * Output: The English status message.
     */
    private String adminBuildRefundProviderMessage(AdminRefundProviderAnswer adminBuildRefundProviderMessageAnswer) {
        String adminBuildRefundProviderMessageWord = adminBuildRefundProviderMessageAnswer
                .adminCheckRefundProviderStatusOutcome().name().toLowerCase(Locale.ROOT);
        String adminBuildRefundProviderMessageSummary = "The payment provider answers: "
                + adminBuildRefundProviderMessageWord.substring(0, 1).toUpperCase(Locale.ROOT)
                + adminBuildRefundProviderMessageWord.substring(1) + ".";
        String adminBuildRefundProviderMessageDetail =
                adminHidePrivateValue(adminBuildRefundProviderMessageAnswer.adminCheckRefundProviderStatusMessage());
        if (adminBuildRefundProviderMessageDetail != null && !adminBuildRefundProviderMessageDetail.isBlank()) {
            return adminBuildRefundProviderMessageSummary + " " + adminBuildRefundProviderMessageDetail;
        }
        return adminBuildRefundProviderMessageSummary;
    }
}
