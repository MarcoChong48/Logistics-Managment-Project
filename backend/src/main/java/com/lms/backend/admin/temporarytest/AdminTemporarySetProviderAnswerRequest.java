// TEMPORARY TEST CODE - remove before integration
// Stage 8 needs to set the stand-in provider's answer for one refund.
package com.lms.backend.admin.temporarytest;

import com.lms.backend.admin.placeholder.AdminRefundProviderOutcome;

public record AdminTemporarySetProviderAnswerRequest(
        Long adminTemporarySetProviderAnswerRefundID,
        AdminRefundProviderOutcome adminTemporarySetProviderAnswerOutcome) {
}
