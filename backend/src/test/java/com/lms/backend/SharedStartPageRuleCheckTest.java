package com.lms.backend;

import static com.lms.backend.shared.login.SharedStartPageRule.sharedFindStartPage;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.lms.backend.shared.value.SharedUserRole;

class SharedStartPageRuleCheckTest {

    // ===== SECTION 1: START PAGE CHECKS =====

    /**
     * What it does: Checks the Admin's start page.
     * Input: The Admin role.
     * Output: Pass when the start page is the dashboard.
     */
    @Test
    void sharedFindStartPageForAdmin() {
        assertEquals("/admin/admin-dashboard.html", sharedFindStartPage(SharedUserRole.ADMIN));
    }

    /**
     * What it does: Checks the Client's start page.
     * Input: The Client role.
     * Output: Pass when the start page is the shared welcome page.
     */
    @Test
    void sharedFindStartPageForClient() {
        assertEquals("/shared/shared-welcome.html", sharedFindStartPage(SharedUserRole.CLIENT));
    }

    /**
     * What it does: Checks the Warehouse Worker's start page.
     * Input: The Warehouse Worker role.
     * Output: Pass when the start page is the shared welcome page.
     */
    @Test
    void sharedFindStartPageForWarehouseWorker() {
        assertEquals("/shared/shared-welcome.html", sharedFindStartPage(SharedUserRole.WAREHOUSE_WORKER));
    }

    /**
     * What it does: Checks the Delivery Worker's start page.
     * Input: The Delivery Worker role.
     * Output: Pass when the start page is the shared welcome page.
     */
    @Test
    void sharedFindStartPageForDeliveryWorker() {
        assertEquals("/shared/shared-welcome.html", sharedFindStartPage(SharedUserRole.DELIVERY_WORKER));
    }
}
