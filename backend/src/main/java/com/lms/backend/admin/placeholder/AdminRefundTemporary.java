package com.lms.backend.admin.placeholder;

import static com.lms.backend.admin.refund.AdminRefundRule.adminCheckRefundBlocksRequest;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_REFUND_STATUS_CHECKED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_REQUESTED;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.AuditLog;
import com.lms.backend.shared.database.AuditLogRepository;
import com.lms.backend.shared.database.Payment;
import com.lms.backend.shared.database.PaymentRepository;
import com.lms.backend.shared.database.Refund;
import com.lms.backend.shared.database.RefundRepository;

@Component
public class AdminRefundTemporary implements AdminRefundPlaceholder {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final PaymentRepository adminRequestFullRefundPaymentRepository;
    private final RefundRepository adminRequestFullRefundRefundRepository;
    private final AuditLogRepository adminRequestFullRefundAuditLogRepository;
    private final Map<Long, AdminRefundProviderOutcome> adminCheckRefundProviderStatusAnswerMap =
            new ConcurrentHashMap<>();

    /**
     * What it does: Connects the refund stand-in to the payment, refund and audit tables.
     * Input: The repositories for those tables.
     * Output: The shared refund stand-in.
     */
    public AdminRefundTemporary(PaymentRepository adminRequestFullRefundPaymentRepository,
            RefundRepository adminRequestFullRefundRefundRepository,
            AuditLogRepository adminRequestFullRefundAuditLogRepository) {
        this.adminRequestFullRefundPaymentRepository = adminRequestFullRefundPaymentRepository;
        this.adminRequestFullRefundRefundRepository = adminRequestFullRefundRefundRepository;
        this.adminRequestFullRefundAuditLogRepository = adminRequestFullRefundAuditLogRepository;
    }

    // ===== SECTION 2: adminRequestFullRefundPlaceholder =====

    /**
     * What it does: Locks the newest positive accepted payment and reuses a blocking refund or saves a new request.
     * Input: The order ID, Admin ID and reason in the caller's transaction.
     * Output: The refund ID and whether this call saved it, or an empty refund ID when nothing was paid.
     * Placeholder: PO-02. The payment integration will issue the real request.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AdminRequestFullRefundAnswer adminRequestFullRefundPlaceholder(Long orderID,
            Long adminRequestFullRefundAdminUserID,
            String adminRequestFullRefundReason) {
        // PLACEHOLDER OUTPUT PO-02.
        // To: Payment.
        // What: A request to refund the newest positive accepted payment in full.
        // How to use: Replace new requests with provider calls. Payment settles any other accepted payments separately.
        Payment adminRequestFullRefundPayment = adminRequestFullRefundPaymentRepository
                .findPaidPaymentByOrderIDForUpdate(orderID)
                .orElse(null);
        if (adminRequestFullRefundPayment == null) {
            return new AdminRequestFullRefundAnswer(null, false);
        }

        // Read refund history only after taking the payment's lock. Check the newest attempt first.
        for (Refund adminRequestFullRefundAttempt : adminRequestFullRefundRefundRepository
                .findByPaymentIDOrderByCreatedTimeDescRefundIDDesc(adminRequestFullRefundPayment.paymentID)) {
            if (!SHARED_REFUND_STATUS_FAILED.equals(adminRequestFullRefundAttempt.refundStatus)) {
                return new AdminRequestFullRefundAnswer(adminRequestFullRefundAttempt.refundID, false);
            }
            AuditLog adminRequestFullRefundLatestCheck = adminRequestFullRefundAuditLogRepository
                    .findFirstByActionAndTargetTypeAndTargetIDOrderByTimestampDescAuditIDDesc(
                            SHARED_AUDIT_ACTION_REFUND_STATUS_CHECKED, "REFUND",
                            adminRequestFullRefundAttempt.refundID.toString())
                    .orElse(null);
            if (adminCheckRefundBlocksRequest(adminRequestFullRefundAttempt.refundStatus,
                    adminRequestFullRefundLatestCheck)) {
                return new AdminRequestFullRefundAnswer(adminRequestFullRefundAttempt.refundID, false);
            }
        }

        Refund adminRequestFullRefundNewRefund = new Refund();
        adminRequestFullRefundNewRefund.paymentID = adminRequestFullRefundPayment.paymentID;
        adminRequestFullRefundNewRefund.amount = adminRequestFullRefundPayment.amount;
        adminRequestFullRefundNewRefund.refundStatus = SHARED_REFUND_STATUS_REQUESTED;
        adminRequestFullRefundNewRefund.createdTime = Instant.now();
        Long refundID = adminRequestFullRefundRefundRepository.save(adminRequestFullRefundNewRefund).refundID;
        return new AdminRequestFullRefundAnswer(refundID, true);
    }

    // ===== SECTION 3: adminCheckRefundProviderStatusPlaceholder =====

    /**
     * What it does: Looks up the stand-in answer for the refund.
     * Input: The refund ID, with its payment locked by the caller.
     * Output: The saved test answer, or Unknown with a message about the provider connection.
     * Placeholder: PI-07. Payment will replace this with a provider status check.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AdminRefundProviderAnswer adminCheckRefundProviderStatusPlaceholder(Long refundID) {
        // PLACEHOLDER INPUT PI-07.
        // From: Payment.
        // What: The provider's recorded result of this refund.
        // How to use: Read the provider's result. The stand-in uses the current in-memory test answer.
        AdminRefundProviderOutcome adminCheckRefundProviderStatusOutcome =
                adminCheckRefundProviderStatusAnswerMap.get(refundID);
        if (adminCheckRefundProviderStatusOutcome == null) {
            return new AdminRefundProviderAnswer(AdminRefundProviderOutcome.UNKNOWN,
                    "No payment provider is connected yet.");
        }
        return new AdminRefundProviderAnswer(adminCheckRefundProviderStatusOutcome, "");
    }

    // ===== SECTION 4: adminRetryRefundPlaceholder =====

    /**
     * What it does: Records a new attempt for the same payment and amount.
     * Input: The old refund ID, the deciding Admin and the reason while the payment is locked.
     * Output: The new requested refund's ID.
     * Placeholder: PO-03. Payment will replace this with a request to the provider.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long adminRetryRefundPlaceholder(Long refundID, Long adminRetryRefundAdminUserID,
            String adminRetryRefundReason) {
        // PLACEHOLDER OUTPUT PO-03.
        // To: Payment.
        // What: A request for a new refund after a confirmed failure.
        // How to use: Issue the new request and save its ID. The stand-in only creates a requested refund.
        Refund adminRetryRefundOldRefund = adminRequestFullRefundRefundRepository.findById(refundID).orElseThrow();
        Refund adminRetryRefundNewRefund = new Refund();
        adminRetryRefundNewRefund.paymentID = adminRetryRefundOldRefund.paymentID;
        adminRetryRefundNewRefund.amount = adminRetryRefundOldRefund.amount;
        adminRetryRefundNewRefund.refundStatus = SHARED_REFUND_STATUS_REQUESTED;
        adminRetryRefundNewRefund.createdTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return adminRequestFullRefundRefundRepository.save(adminRetryRefundNewRefund).refundID;
    }

    // ===== SECTION 5: TEST-MODE PROVIDER ANSWER =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 8 sets the provider answer through the admintest service.
    /**
     * What it does: Holds the answer to return on subsequent checks in this application run.
     * Input: An existing refund ID and the test provider's outcome.
     * Output: None. The next check looks up this choice until it is changed or the application stops.
     */
    public void adminTemporarySetProviderAnswer(Long refundID,
            AdminRefundProviderOutcome adminTemporarySetProviderAnswerOutcome) {
        adminCheckRefundProviderStatusAnswerMap.put(refundID, adminTemporarySetProviderAnswerOutcome);
    }
}
