package com.lms.backend.admin.product;

import java.util.List;

public record AdminSearchProductResponse(
        List<AdminSearchProductRow> adminSearchProductRowList,
        int adminSearchProductPageNumber,
        int adminSearchProductTotalPageCount,
        long adminSearchProductTotalRowCount) {
}
