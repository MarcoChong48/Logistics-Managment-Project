package com.lms.backend.admin.order;

import java.util.List;

// F5.1: public order data for the Admin screens.
public record AdminSearchOrderResponse(
        List<AdminSearchOrderRow> adminSearchOrderRowList,
        int adminSearchOrderPageNumber,
        int adminSearchOrderTotalPageCount,
        long adminSearchOrderTotalRowCount) {
}
