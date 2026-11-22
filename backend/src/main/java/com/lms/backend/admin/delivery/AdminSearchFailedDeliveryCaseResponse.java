package com.lms.backend.admin.delivery;

import java.util.List;

/** One page of matching failed delivery cases and the review thresholds used to read them. */
public record AdminSearchFailedDeliveryCaseResponse(
        List<AdminSearchFailedDeliveryCaseRow> adminSearchFailedDeliveryCaseRowList,
        int adminSearchFailedDeliveryCasePageNumber,
        int adminSearchFailedDeliveryCaseTotalPageCount,
        long adminSearchFailedDeliveryCaseTotalRowCount,
        int adminSearchFailedDeliveryCaseReviewFailureCount,
        int adminSearchFailedDeliveryCaseReminderDays,
        int adminSearchFailedDeliveryCaseFinalReviewDays) {
}
