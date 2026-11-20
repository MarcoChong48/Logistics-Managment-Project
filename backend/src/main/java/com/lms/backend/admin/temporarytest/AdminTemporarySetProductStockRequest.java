// TEMPORARY TEST CODE - remove before integration
// Stage 4 needs explicit stock numbers to check availability and shortfall.
package com.lms.backend.admin.temporarytest;

import java.math.BigDecimal;

public record AdminTemporarySetProductStockRequest(String adminTemporarySetProductStockSKU,
        BigDecimal adminTemporarySetProductStockPhysicalStock, BigDecimal adminTemporarySetProductStockReservedStock) {
}
