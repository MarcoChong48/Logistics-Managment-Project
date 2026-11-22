package com.lms.backend.warehouse;

import java.util.UUID;

/**
 * What it does: Carries the text a scanner or the worker typed into the scan box.
 */
public record WarehouseRecordScanRequest(
        String scannedIdentifier,
        UUID requestId) {
}
