package com.lms.backend;

import static com.lms.backend.admin.dashboard.AdminDashboardRule.adminCountActiveOrder;
import static com.lms.backend.admin.dashboard.AdminDashboardRule.adminCountOpenCaseTotal;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AdminDashboardRuleCheckTest {

    // ===== SECTION 1: DASHBOARD COUNTS =====

    // What it does: Keeps terminal orders out of the active total.
    // Input: Counts for several statuses, including an unfamiliar one.
    // Output: Only DELIVERED and CANCELLED are excluded.
    @Test
    void adminCheckDashboardActiveOrderCount() {
        assertEquals(9, adminCountActiveOrder(Map.of(
                "PICKING", 4L, "DELIVERY_FAILED", 3L, "NEW_STATUS", 2L, "DELIVERED", 10L, "CANCELLED", 20L)));
        assertEquals(0, adminCountActiveOrder(Map.of()));
    }

    // What it does: Adds the queues that make up the open cases total.
    // Input: Open shortages, pending client requests and unresolved refunds.
    // Output: Their sum, including zero when all queues are empty.
    @Test
    void adminCheckDashboardOpenCaseTotal() {
        assertEquals(29, adminCountOpenCaseTotal(12, 8, 9));
        assertEquals(0, adminCountOpenCaseTotal(0, 0, 0));
    }
}
