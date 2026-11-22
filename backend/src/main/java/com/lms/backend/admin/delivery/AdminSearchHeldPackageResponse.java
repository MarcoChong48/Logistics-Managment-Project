package com.lms.backend.admin.delivery;

import java.util.List;

/** F7.7: A page of held packages with its page information. */
public record AdminSearchHeldPackageResponse(
        List<AdminSearchHeldPackageRow> adminSearchHeldPackageRowList,
        int adminSearchHeldPackagePageNumber,
        int adminSearchHeldPackageTotalPageCount,
        long adminSearchHeldPackageTotalRowCount) {
}
