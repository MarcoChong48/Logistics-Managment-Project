package com.lms.backend.admin.order;

import java.time.Instant;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderShortageCaseRow(
        Long shortageCaseID,
        Long productID,
        String adminViewOrderShortageCaseSKU,
        int missingQuantity,
        String caseStatus,
        String decision,
        Instant reportedTime,
        Instant decidedTime) {
}
