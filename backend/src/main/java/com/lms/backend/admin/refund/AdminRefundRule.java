package com.lms.backend.admin.refund;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_REFUND_STATUS_REFUNDED;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.lms.backend.admin.placeholder.AdminRefundProviderOutcome;
import com.lms.backend.shared.database.AuditLog;

public final class AdminRefundRule {

    // ===== SECTION 1: PURE RULES =====

    /**
     * What it does: Keeps the pure refund rules on the class.
     * Input: None.
     * Output: No object is needed.
     */
    private AdminRefundRule() {
    }

    // ===== SECTION 2: adminCheckRefundIsUnresolved =====

    /**
     * What it does: Checks whether a refund still carries an open case.
     * Input: The stored status word and the optional replacement refund ID.
     * Output: True when no newer attempt exists and the provider has not completed this attempt.
     */
    public static boolean adminCheckRefundIsUnresolved(String refundStatus, Long adminSearchRefundReplacedByRefundID) {
        return adminSearchRefundReplacedByRefundID == null && !SHARED_REFUND_STATUS_REFUNDED.equals(refundStatus);
    }

    // ===== SECTION 3: adminCountRefundAgeHours =====

    /**
     * What it does: Counts complete hours from creation to completion or the current time.
     * Input: The creation time and the relevant end time.
     * Output: The age in whole hours.
     */
    public static long adminCountRefundAgeHours(Instant createdTime, Instant adminCountRefundAgeHoursEndTime) {
        return Math.max(0, ChronoUnit.HOURS.between(createdTime, adminCountRefundAgeHoursEndTime));
    }

    // ===== SECTION 4: EARLIER REFUND RULE =====

    /**
     * What it does: Applies the same earlier-attempt check to a full request and a retry.
     * Input: The stored status and the latest saved provider check, if there is one.
     * Output: True when this attempt blocks opening a new refund.
     */
    public static boolean adminCheckRefundBlocksRequest(String refundStatus,
            AuditLog adminCheckRefundBlocksRequestLastCheck) {
        if (!SHARED_REFUND_STATUS_FAILED.equals(refundStatus)) {
            return true;
        }

        // Legacy failures without a provider result remain eligible as earlier history.
        return adminCheckRefundBlocksRequestLastCheck != null
                && (adminCheckRefundBlocksRequestLastCheck.newValue == null
                        || !AdminRefundProviderOutcome.FAILED.name().equals(
                                adminCheckRefundBlocksRequestLastCheck.newValue.get("providerOutcome")));
    }
}
