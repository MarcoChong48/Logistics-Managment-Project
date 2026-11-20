package com.lms.backend.admin.log;

import java.util.List;

// The filtered log page and the total size of the filtered set.
public record AdminSearchSecurityLogResponse(
        List<AdminSearchSecurityLogRow> adminSearchSecurityLogRowList,
        int adminSearchSecurityLogPageNumber,
        int adminSearchSecurityLogTotalPageCount,
        long adminSearchSecurityLogTotalRowCount) {
}
