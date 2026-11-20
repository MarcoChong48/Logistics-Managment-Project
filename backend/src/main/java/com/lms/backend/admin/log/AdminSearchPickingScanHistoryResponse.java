package com.lms.backend.admin.log;

import java.util.List;

// The filtered log page and the total size of the filtered set.
public record AdminSearchPickingScanHistoryResponse(
        List<AdminSearchPickingScanHistoryRow> adminSearchPickingScanHistoryRowList,
        int adminSearchPickingScanHistoryPageNumber,
        int adminSearchPickingScanHistoryTotalPageCount,
        long adminSearchPickingScanHistoryTotalRowCount) {
}
