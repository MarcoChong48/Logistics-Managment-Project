package com.lms.backend.admin.log;

import java.util.List;

// The filtered log page and the total size of the filtered set.
public record AdminSearchAuditLogResponse(
        List<AdminSearchAuditLogRow> adminSearchAuditLogRowList,
        int adminSearchAuditLogPageNumber,
        int adminSearchAuditLogTotalPageCount,
        long adminSearchAuditLogTotalRowCount) {
}
