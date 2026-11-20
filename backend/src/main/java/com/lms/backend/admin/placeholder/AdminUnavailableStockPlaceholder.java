package com.lms.backend.admin.placeholder;

public interface AdminUnavailableStockPlaceholder {

    // ===== SECTION 1: WAREHOUSE INPUT =====

    /**
     * What it does: Reads how many physical units cannot be sold.
     * Input: The product ID.
     * Output: The number of unavailable units.
     * Placeholder: PI-06. Warehouse provides damaged or held stock when its module is connected.
     */
    int adminReadUnavailableStockPlaceholder(Long productID);
}
