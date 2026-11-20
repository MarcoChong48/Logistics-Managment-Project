package com.lms.backend.admin.product;

public record AdminImportProductCSVRejectedLine(
        int adminImportProductCSVLineNumber,
        String adminImportProductCSVRejectReason) {
}
