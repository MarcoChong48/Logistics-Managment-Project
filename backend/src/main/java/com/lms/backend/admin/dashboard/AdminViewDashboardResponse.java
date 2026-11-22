package com.lms.backend.admin.dashboard;

import java.util.List;

// ===== SECTION 1: DASHBOARD RESPONSE =====

public record AdminViewDashboardResponse(
        List<AdminViewDashboardOrderStatusCount> adminViewDashboardOrderStatusCountList,
        long adminViewDashboardActiveOrderCount,
        long adminViewDashboardLowStockProductCount,
        List<AdminViewDashboardLowStockProduct> adminViewDashboardLowStockProductList,
        long adminViewDashboardOpenShortageCaseCount,
        long adminViewDashboardWaitingShortageCaseCount,
        long adminViewDashboardPendingClientInstructionCount,
        long adminViewDashboardWaitingClientCaseCount,
        long adminViewDashboardFlaggedDeliveryCaseCount,
        long adminViewDashboardUnresolvedRefundCount,
        long adminViewDashboardLockedUserCount,
        long adminViewDashboardOpenCaseTotalCount) {
}
