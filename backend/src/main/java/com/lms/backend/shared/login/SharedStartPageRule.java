package com.lms.backend.shared.login;

import com.lms.backend.shared.value.SharedUserRole;

public final class SharedStartPageRule {

    // ===== SECTION 1: CONSTRUCTOR =====

    /**
     * What it does: Keeps the static rule from being created as an object.
     * Input: None.
     * Output: None.
     */
    private SharedStartPageRule() {
    }

    // ===== SECTION 2: sharedFindStartPage =====

    /**
     * What it does: Picks the first page for the user's role after login.
     * Input: The saved account role.
     * Output: The role's start page web address.
     * Placeholder: PO-10. Client, Warehouse and Delivery owners set their own start pages here.
     *              Until then those roles use the welcome page.
     */
    public static String sharedFindStartPage(SharedUserRole role) {
        // PLACEHOLDER OUTPUT PO-10
        // To: Client, Warehouse and Delivery owners.
        // What: The first page for each role after login.
        // How to use: Replace the welcome address on your role's line when its first page is ready.
        return switch (role) {
            case ADMIN -> "/admin/admin-dashboard.html";
            case CLIENT -> "/shared/shared-welcome.html";
            case WAREHOUSE_WORKER -> "/shared/shared-welcome.html";
            case DELIVERY_WORKER -> "/shared/shared-welcome.html";
        };
    }
}
