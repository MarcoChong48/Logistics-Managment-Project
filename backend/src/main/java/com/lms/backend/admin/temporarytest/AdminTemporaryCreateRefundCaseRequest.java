// TEMPORARY TEST CODE - remove before integration
// Stage 8 needs refund cases with a controlled status and age.
package com.lms.backend.admin.temporarytest;

import java.math.BigDecimal;

public record AdminTemporaryCreateRefundCaseRequest(
        String adminTemporaryCreateRefundCaseRefundStatus,
        BigDecimal adminTemporaryCreateRefundCaseAgeHours) {
}
