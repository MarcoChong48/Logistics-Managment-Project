package com.lms.backend.admin.shortage;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_WAREHOUSE;

import com.lms.backend.shared.value.SharedOrderStatus;

public final class AdminShortageRule {

    // ===== SECTION 1: CONSTRUCTOR =====

    /**
     * What it does: Keeps the pure shortage rule from being created as an object.
     * Input: None.
     * Output: None.
     */
    private AdminShortageRule() {
    }

    // ===== SECTION 2: CANCELLATION RULE =====

    /**
     * What it does: Tests whether the order state and package custody permit shortage cancellation.
     * Input: The order status, whether it has a package and its custody status.
     * Output: True if the order can be cancelled after the other decision rules pass.
     */
    public static boolean adminCheckShortageCancelIsAllowed(String orderStatus,
            boolean adminCheckShortageCancelHasPackage, String custodyStatus) {
        // In-transit and final orders cannot be cancelled in the shortage workflow.
        if (SharedOrderStatus.SHIPPED.name().equals(orderStatus)
                || SharedOrderStatus.OUT_FOR_DELIVERY.name().equals(orderStatus)
                || SharedOrderStatus.DELIVERED.name().equals(orderStatus)
                || SharedOrderStatus.CANCELLED.name().equals(orderStatus)) {
            return false;
        }

        // When a package exists, Warehouse must have physical custody to cancel.
        return !adminCheckShortageCancelHasPackage || SHARED_CUSTODY_STATUS_WAREHOUSE.equals(custodyStatus);
    }
}
