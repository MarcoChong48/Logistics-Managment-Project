package com.lms.backend.admin.placeholder;

import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_REFUND_STATUS_CHECKED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_REQUESTED;

import java.time.Instant;
import java.util.Optional;

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
     * What it does: Locks the accepted payment and reuses a blocking refund or saves a new request.
     * Input: The order ID, Admin ID and reason in the caller's transaction.
     * Output: The refund ID, or empty if nothing had been paid.
     * Placeholder: PO-02. The payment integration will issue the real request.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> adminRequestFullRefundPlaceholder(Long orderID, Long adminRequestFullRefundAdminUserID,
            String adminRequestFullRefundReason) {
        // PLACEHOLDER OUTPUT PO-02.
        // To: Payment.
        // What: A request to refund the whole accepted payment.
        // How to use: Replace the saved REQUESTED row with the provider-backed request and its saved result.
        Payment adminRequestFullRefundPayment = adminRequestFullRefundPaymentRepository
                .findFirstByOrderIDAndAcceptedTimeIsNotNullOrderByCreatedTimeDescPaymentIDDesc(orderID)
                .orElse(null);
        if (adminRequestFullRefundPayment == null || adminRequestFullRefundPayment.amount.signum() == 0) {
            return Optional.empty();
        }

        // Read refund history only after taking the payment's lock. Check the newest attempt first.
        for (Refund adminRequestFullRefundAttempt : adminRequestFullRefundRefundRepository
                .findByPaymentIDOrderByCreatedTimeDescRefundIDDesc(adminRequestFullRefundPayment.paymentID)) {
            if (!SHARED_REFUND_STATUS_FAILED.equals(adminRequestFullRefundAttempt.refundStatus)) {
                return Optional.of(adminRequestFullRefundAttempt.refundID);
            }
            AuditLog adminRequestFullRefundLatestCheck = adminRequestFullRefundAuditLogRepository
                    .findFirstByActionAndTargetTypeAndTargetIDOrderByTimestampDescAuditIDDesc(
                            SHARED_AUDIT_ACTION_REFUND_STATUS_CHECKED, "REFUND",
                            adminRequestFullRefundAttempt.refundID.toString())
                    .orElse(null);
            if (adminRequestFullRefundLatestCheck != null && (adminRequestFullRefundLatestCheck.newValue == null
                    || !SHARED_REFUND_STATUS_FAILED.equals(adminRequestFullRefundLatestCheck.newValue.get("providerOutcome")))) {
                return Optional.of(adminRequestFullRefundAttempt.refundID);
            }
        }

        Refund adminRequestFullRefundNewRefund = new Refund();
        adminRequestFullRefundNewRefund.paymentID = adminRequestFullRefundPayment.paymentID;
        adminRequestFullRefundNewRefund.amount = adminRequestFullRefundPayment.amount;
        adminRequestFullRefundNewRefund.refundStatus = SHARED_REFUND_STATUS_REQUESTED;
        adminRequestFullRefundNewRefund.createdTime = Instant.now();
        return Optional.of(adminRequestFullRefundRefundRepository.save(adminRequestFullRefundNewRefund).refundID);
    }
}
