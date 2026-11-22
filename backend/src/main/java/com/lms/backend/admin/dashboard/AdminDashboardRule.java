package com.lms.backend.admin.dashboard;

import java.util.Map;

import com.lms.backend.shared.value.SharedOrderStatus;

public final class AdminDashboardRule {

    // ===== SECTION 1: PURE RULES =====

    /**
     * What it does: Keeps the pure dashboard rules on the class.
     * Input: None.
     * Output: No object is needed.
     */
    private AdminDashboardRule() {
    }

    // ===== SECTION 2: adminCountActiveOrder =====

    /**
     * What it does: Includes every order except delivered and cancelled orders.
     * Input: The counts by stored order status.
     * Output: The active order count.
     */
    public static long adminCountActiveOrder(Map<String, Long> adminCountActiveOrderStatusCountMap) {
        return adminCountActiveOrderStatusCountMap.entrySet().stream()
                .filter(adminCountActiveOrderStatusCount ->
                        !SharedOrderStatus.DELIVERED.name().equals(adminCountActiveOrderStatusCount.getKey())
                        && !SharedOrderStatus.CANCELLED.name().equals(adminCountActiveOrderStatusCount.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    // ===== SECTION 3: adminCountOpenCaseTotal =====

    /**
     * What it does: Adds the work queues that need an Admin decision or check.
     * Input: Open shortages, pending client requests and unresolved refunds.
     * Output: The open case total.
     */
    public static long adminCountOpenCaseTotal(long adminCountOpenCaseTotalOpenShortageCount,
            long adminCountOpenCaseTotalPendingRequestCount, long adminCountOpenCaseTotalUnresolvedRefundCount) {
        return adminCountOpenCaseTotalOpenShortageCount + adminCountOpenCaseTotalPendingRequestCount
                + adminCountOpenCaseTotalUnresolvedRefundCount;
    }
}
