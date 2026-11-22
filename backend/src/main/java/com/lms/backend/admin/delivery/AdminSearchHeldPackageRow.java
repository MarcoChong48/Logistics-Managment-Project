package com.lms.backend.admin.delivery;

import java.time.Instant;

import com.lms.backend.shared.value.SharedAccountStatus;

/** F7.7: The saved custody and current delivery responsibility for a package. */
public record AdminSearchHeldPackageRow(
        Long packageID,
        String packageQRCode,
        Long orderID,
        String adminSearchHeldPackageOrderReference,
        String adminSearchHeldPackageOrderStatus,
        Long currentHandlerUserID,
        String adminSearchHeldPackageCurrentHandlerUsername,
        SharedAccountStatus adminSearchHeldPackageCurrentHandlerAccountStatus,
        Long adminSearchHeldPackageResponsibleWorkerUserID,
        String adminSearchHeldPackageResponsibleWorkerUsername,
        Instant adminSearchHeldPackageLatestReassignmentTime) {
}
