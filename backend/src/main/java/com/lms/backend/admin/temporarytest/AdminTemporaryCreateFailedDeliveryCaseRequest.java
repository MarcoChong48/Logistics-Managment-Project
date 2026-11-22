// TEMPORARY TEST CODE - remove before integration
// Stage 7 needs failed delivery cases with controlled attempts, return age and request state.
package com.lms.backend.admin.temporarytest;

import java.math.BigDecimal;

public record AdminTemporaryCreateFailedDeliveryCaseRequest(
        BigDecimal adminTemporaryCreateFailedDeliveryCaseFailureCount,
        Boolean adminTemporaryCreateFailedDeliveryCaseIsReturned,
        BigDecimal adminTemporaryCreateFailedDeliveryCaseDaysSinceReturn,
        String adminTemporaryCreateFailedDeliveryCaseClientRequest) {
}
