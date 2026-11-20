package com.lms.backend.admin.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.lms.backend.shared.error.SharedRuleException;

public final class AdminProductRule {

    // ===== SECTION 1: CONSTANTS =====

    public static final int ADMIN_PRODUCT_SKU_MINIMUM_LENGTH = 3;
    public static final int ADMIN_PRODUCT_SKU_MAXIMUM_LENGTH = 60;
    public static final int ADMIN_PRODUCT_NAME_MINIMUM_LENGTH = 2;
    public static final int ADMIN_PRODUCT_NAME_MAXIMUM_LENGTH = 200;
    public static final BigDecimal ADMIN_PRODUCT_MAXIMUM_PRICE = new BigDecimal("9999999.99");
    public static final int ADMIN_PRODUCT_MAXIMUM_THRESHOLD = 1000000;
    public static final int ADMIN_PRODUCT_IMPORT_MAXIMUM_LINE_COUNT = 500;
    public static final int ADMIN_PRODUCT_IMPORT_MAXIMUM_FILE_BYTES = 1048576;
    public static final int ADMIN_PRODUCT_QR_CODE_IMAGE_SIZE = 300;

    // ===== SECTION 2: CATALOGUE INPUT =====

    /**
     * What it does: Normalizes a SKU and checks the catalogue's length and alphabet rules.
     * Input: The value entered by the Admin.
     * Output: The uppercase SKU, or 400 ADMIN_SKU_INVALID.
     */
    public static String adminCheckSKURule(String adminCheckSKURuleValue) {
        // Use the same uppercase conversion regardless of the server's language.
        String SKU = adminCheckSKURuleValue == null ? "" : adminCheckSKURuleValue.strip().toUpperCase(Locale.ROOT);
        if (SKU.length() < ADMIN_PRODUCT_SKU_MINIMUM_LENGTH || SKU.length() > ADMIN_PRODUCT_SKU_MAXIMUM_LENGTH) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_SKU_INVALID",
                    "The SKU must have 3 to 60 characters.");
        }
        if (!SKU.matches("[A-Z0-9-]+")) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_SKU_INVALID",
                    "The SKU may hold only letters, digits and hyphens.");
        }
        return SKU;
    }

    /**
     * What it does: Reads a price and checks that it fits the catalogue's currency rules.
     * Input: The raw price text.
     * Output: The exact two-decimal price, or 400 ADMIN_PRODUCT_INPUT_INVALID.
     */
    public static BigDecimal adminCheckPriceRule(String adminCheckPriceRuleValue) {
        BigDecimal currentPrice;
        try {
            currentPrice = new BigDecimal(adminCheckPriceRuleValue == null ? "" : adminCheckPriceRuleValue.strip());
        } catch (NumberFormatException adminCheckPriceRuleError) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_PRODUCT_INPUT_INVALID",
                    "The price is not a number.");
        }
        // Test the scale before formatting so fractions of a cent are never rounded away.
        if (currentPrice.signum() <= 0 || currentPrice.compareTo(ADMIN_PRODUCT_MAXIMUM_PRICE) > 0
                || currentPrice.scale() > 2) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_PRODUCT_INPUT_INVALID",
                    "The price must be above 0 and at most 9,999,999.99, with at most 2 decimal places.");
        }
        return currentPrice.setScale(2, RoundingMode.UNNECESSARY);
    }

    // ===== SECTION 3: STOCK COUNTS =====

    /**
     * What it does: Calculates how many units are available to new orders.
     * Input: Physical stock, reserved stock and unavailable stock.
     * Output: Available units, never below zero.
     */
    public static int adminCalculateAvailableStock(int physicalStock, int reservedStock,
            int adminCalculateAvailableStockUnavailableStock) {
        // Widen before subtracting because both deductions may be large.
        return (int) Math.max(0L, (long) physicalStock - reservedStock - adminCalculateAvailableStockUnavailableStock);
    }

    /**
     * What it does: Counts how many reserved or unavailable units exceed physical stock.
     * Input: Physical stock, reserved stock and unavailable stock.
     * Output: The number of units short, or zero if the physical stock covers them.
     */
    public static long adminCalculateReservationShortfall(int physicalStock, int reservedStock,
            int adminCalculateReservationShortfallUnavailableStock) {
        return Math.max(0L, (long) reservedStock + adminCalculateReservationShortfallUnavailableStock - physicalStock);
    }

    /**
     * What it does: Decides whether a product needs a low-stock flag.
     * Input: Available units and the catalogue threshold.
     * Output: True when the available units are strictly below the threshold.
     */
    public static boolean adminCheckLowStock(int adminCheckLowStockAvailableStock, int lowStockThreshold) {
        return adminCheckLowStockAvailableStock < lowStockThreshold;
    }

    // ===== SECTION 4: CONSTRUCTOR =====

    /**
     * What it does: Keeps the pure rules from being created as an object.
     * Input: None.
     * Output: None.
     */
    private AdminProductRule() {
    }
}
