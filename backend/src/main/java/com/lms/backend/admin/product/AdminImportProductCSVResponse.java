package com.lms.backend.admin.product;

import java.util.List;

public record AdminImportProductCSVResponse(
        int adminImportProductCSVCreatedCount,
        int adminImportProductCSVRejectedCount,
        List<AdminImportProductCSVRejectedLine> adminImportProductCSVRejectedLineList) {
}
