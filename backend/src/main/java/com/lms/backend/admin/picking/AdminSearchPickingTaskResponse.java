package com.lms.backend.admin.picking;

import java.util.List;

// F6.8: One page of picking tasks and its totals.
public record AdminSearchPickingTaskResponse(
        List<AdminSearchPickingTaskRow> adminSearchPickingTaskRowList,
        int adminSearchPickingTaskPageNumber,
        int adminSearchPickingTaskTotalPageCount,
        long adminSearchPickingTaskTotalRowCount) {
}
