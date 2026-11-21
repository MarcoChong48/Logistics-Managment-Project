package com.lms.backend;

import static com.lms.backend.admin.shortage.AdminShortageRule.adminCheckShortageCancelIsAllowed;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Database-free checks for the shortage cancellation rule. */
class AdminShortageRuleCheckTest {

    // ===== SECTION 1: ORDER STATE AND CUSTODY =====

    /**
     * What it does: Checks the order and custody cases that can still be cancelled.
     * Input: Each allowed order status, package presence and custody.
     * Output: A true result in every listed case.
     */
    @ParameterizedTest
    @CsvSource({
        "AWAITING_PAYMENT, false, ",
        "READY_FOR_PICKING, false, ",
        "PICKING, false, ",
        "READY_FOR_PICKUP, true, WAREHOUSE",
        "DELIVERY_FAILED, true, WAREHOUSE",
        "READY_FOR_REDELIVERY, true, WAREHOUSE"
    })
    void adminCheckAllowedShortageCancellation(String orderStatus, boolean adminCheckAllowedShortageCancellationHasPackage,
            String custodyStatus) {
        assertTrue(adminCheckShortageCancelIsAllowed(
                orderStatus, adminCheckAllowedShortageCancellationHasPackage, custodyStatus));
    }

    /**
     * What it does: Checks states that rule out cancellation or leave custody with Delivery.
     * Input: Each blocked order state or custody case.
     * Output: A false result in every listed case.
     */
    @ParameterizedTest
    @CsvSource({
        "SHIPPED, true, DELIVERY",
        "OUT_FOR_DELIVERY, true, DELIVERY",
        "DELIVERED, true, DELIVERY",
        "CANCELLED, true, WAREHOUSE",
        "DELIVERY_FAILED, true, DELIVERY"
    })
    void adminCheckRefusedShortageCancellation(String orderStatus, boolean adminCheckRefusedShortageCancellationHasPackage,
            String custodyStatus) {
        assertFalse(adminCheckShortageCancelIsAllowed(
                orderStatus, adminCheckRefusedShortageCancellationHasPackage, custodyStatus));
    }
}
