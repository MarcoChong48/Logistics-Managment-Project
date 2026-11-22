package com.lms.backend.admin.dashboard;

// ===== SECTION 1: LOW-STOCK PRODUCT =====

public record AdminViewDashboardLowStockProduct(
        Long productID,
        String SKU,
        String name,
        int lowStockThreshold,
        int adminViewDashboardLowStockProductAvailableStock) {
}
