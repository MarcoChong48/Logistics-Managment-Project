package com.lms.backend.admin.placeholder;

import org.springframework.stereotype.Component;

@Component
public class AdminUnavailableStockTemporary implements AdminUnavailableStockPlaceholder {

    // ===== SECTION 1: WAREHOUSE INPUT =====

    /**
     * What it does: Supplies a stock number until Warehouse can report unavailable units.
     * Input: The product ID.
     * Output: Zero until Warehouse is connected.
     * Placeholder: PI-06. Warehouse replaces this component with a reader for damaged or held stock.
     */
    @Override
    public int adminReadUnavailableStockPlaceholder(Long productID) {
        // PLACEHOLDER INPUT PI-06.
        // From: Warehouse.
        // What: The damaged or held units currently included in physical stock.
        // How to use: Warehouse replaces this component with its real read. Until then use zero.
        return 0;
    }
}
