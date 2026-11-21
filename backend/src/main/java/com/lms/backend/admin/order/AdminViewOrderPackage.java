package com.lms.backend.admin.order;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderPackage(
        Long packageID,
        String packageQRCode,
        String custodyStatus,
        Long currentHandlerUserID,
        String adminViewOrderPackageCurrentHandlerUsername,
        String adminViewOrderPackageResponsibleWorkerUsername) {
}
