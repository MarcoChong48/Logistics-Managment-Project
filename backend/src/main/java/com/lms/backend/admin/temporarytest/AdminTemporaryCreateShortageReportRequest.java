// TEMPORARY TEST CODE - remove before integration
package com.lms.backend.admin.temporarytest;

import com.lms.backend.shared.value.SharedOrderStatus;

public record AdminTemporaryCreateShortageReportRequest(
        SharedOrderStatus adminTemporaryCreateShortageReportOrderStatus,
        Integer adminTemporaryCreateShortageReportMissingQuantity,
        String adminTemporaryCreateShortageReportOrderReference) {
}
