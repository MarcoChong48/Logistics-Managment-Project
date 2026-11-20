package com.lms.backend;

import static com.lms.backend.admin.product.AdminProductRule.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.lms.backend.shared.error.SharedRuleException;

class AdminProductRuleCheckTest {

    // ===== SECTION 1: SKU AND PRICE =====

    /**
     * What it does: Checks accepted SKU limits and uppercase normalization.
     * Input: None. Uses fixed SKU examples.
     * Output: A test result for the valid SKUs.
     */
    @Test
    @DisplayName("SKU accepts 3 and 60 characters and normalizes lowercase")
    void adminCheckSKURuleAcceptsLimits() {
        assertEquals("A-1", adminCheckSKURule("a-1"));
        assertEquals("A".repeat(60), adminCheckSKURule("a".repeat(60)));
        assertEquals("MUG-01", adminCheckSKURule("  mug-01  "));
    }

    /**
     * What it does: Checks invalid SKU spelling and short lengths.
     * Input: One invalid SKU.
     * Output: A test result for the SKU field error.
     */
    @ParameterizedTest
    @ValueSource(strings = {"AB", "A B", "A_B"})
    void adminCheckSKURuleRefusesInvalidText(String adminCheckSKURuleValue) {
        assertEquals("ADMIN_SKU_INVALID",
                assertThrows(SharedRuleException.class, () -> adminCheckSKURule(adminCheckSKURuleValue)).getCode());
    }

    /**
     * What it does: Checks the maximum SKU length.
     * Input: None. Uses a 61-character SKU.
     * Output: A test result for the maximum-length rule.
     */
    @Test
    void adminCheckSKURuleRefusesLongSKU() {
        assertEquals("ADMIN_SKU_INVALID",
                assertThrows(SharedRuleException.class, () -> adminCheckSKURule("A".repeat(61))).getCode());
    }

    /**
     * What it does: Checks that valid prices maintain their full decimal precision.
     * Input: A valid price and its two-decimal form.
     * Output: A test result for the price rule.
     */
    @ParameterizedTest
    @CsvSource({"0.01, 0.01", "9999999.99, 9999999.99", "89.5, 89.50"})
    void adminCheckPriceRuleAcceptsLimits(String adminCheckPriceRuleValue, String adminCheckPriceRuleExpectedValue) {
        assertEquals(new BigDecimal(adminCheckPriceRuleExpectedValue), adminCheckPriceRule(adminCheckPriceRuleValue));
    }

    /**
     * What it does: Checks that invalid prices are refused before saving.
     * Input: An invalid price.
     * Output: A test result for the price field error.
     */
    @ParameterizedTest
    @ValueSource(strings = {"0", "10000000", "1.999", "-5", "abc"})
    void adminCheckPriceRuleRefusesInvalidPrice(String adminCheckPriceRuleValue) {
        assertEquals("ADMIN_PRODUCT_INPUT_INVALID",
                assertThrows(SharedRuleException.class, () -> adminCheckPriceRule(adminCheckPriceRuleValue)).getCode());
    }

    // ===== SECTION 2: STOCK COUNTS =====

    /**
     * What it does: Checks the available stock and shortfall calculations together.
     * Input: The three stock quantities and their expected derived quantities.
     * Output: A test result for the stock arithmetic.
     */
    @ParameterizedTest
    @CsvSource({"10, 3, 0, 7, 0", "10, 3, 2, 5, 0", "5, 8, 0, 0, 3", "0, 2147483647, 1, 0, 2147483648"})
    void adminCalculateAvailableStockAndShortfall(int physicalStock, int reservedStock,
            int adminCalculateAvailableStockUnavailableStock, int adminCalculateAvailableStockExpectedStock,
            long adminCalculateReservationShortfallExpectedStock) {
        assertEquals(adminCalculateAvailableStockExpectedStock, adminCalculateAvailableStock(
                physicalStock, reservedStock, adminCalculateAvailableStockUnavailableStock));
        assertEquals(adminCalculateReservationShortfallExpectedStock, adminCalculateReservationShortfall(
                physicalStock, reservedStock, adminCalculateAvailableStockUnavailableStock));
    }

    /**
     * What it does: Checks the strict low-stock threshold and the zero-threshold rule.
     * Input: Available stock, threshold and expected flag.
     * Output: A test result for the low-stock flag.
     */
    @ParameterizedTest
    @CsvSource({"5, 8, true", "8, 8, false", "9, 8, false", "0, 0, false"})
    void adminCheckLowStockAgainstThreshold(int adminCheckLowStockAvailableStock, int lowStockThreshold,
            boolean adminCheckLowStockExpectedFlag) {
        assertEquals(adminCheckLowStockExpectedFlag, adminCheckLowStock(adminCheckLowStockAvailableStock,
                lowStockThreshold));
    }
}
