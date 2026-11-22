package com.lms.backend.admin.refund;

import java.util.List;

// F8.1: the visible refund page and matching counts.
public record AdminSearchRefundResponse(
        List<AdminSearchRefundRow> adminSearchRefundRowList,
        int adminSearchRefundPageNumber,
        int adminSearchRefundTotalPageCount,
        long adminSearchRefundTotalRowCount) {
}
