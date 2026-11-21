package com.lms.backend.admin.shortage;

import java.time.Instant;

/** The safe facts shown in a row of the warehouse shortage case list. */
public record AdminSearchShortageCaseRow(
        long shortageCaseID,
        long orderID,
        String adminSearchShortageCaseOrderReference,
        String adminSearchShortageCaseOrderStatus,
        boolean adminSearchShortageCaseOrderIsPaid,
        long productID,
        String adminSearchShortageCaseSKU,
        String adminSearchShortageCaseProductName,
        int missingQuantity,
        int adminSearchShortageCaseOrderedQuantity,
        String reportNote,
        long reportedByWorkerID,
        String adminSearchShortageCaseReportedByUsername,
        Instant reportedTime,
        String caseStatus,
        String decision,
        Long decidedByAdminID,
        String adminSearchShortageCaseDecidedByUsername,
        Instant decidedTime,
        String decisionNote,
        Instant resolvedTime) {
}
