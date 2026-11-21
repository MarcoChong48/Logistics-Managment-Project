package com.lms.backend.admin.log;

import java.time.Instant;

import com.lms.backend.shared.value.SharedScanResult;

// Public fields for one row of the pickingscanhistory log search.
public record AdminSearchPickingScanHistoryRow(
        Long scanID,
        Instant scanTime,
        Long pickingTaskID,
        Long orderID,
        String adminSearchPickingScanHistoryOrderReference,
        Long workerUserID,
        String adminSearchPickingScanHistoryWorkerUsername,
        String scannedIdentifier,
        SharedScanResult result,
        String reason,
        Long originalScanID,
        String adminSearchPickingScanHistoryProductSKU) {
}
