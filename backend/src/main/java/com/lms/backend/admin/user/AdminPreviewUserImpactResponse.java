package com.lms.backend.admin.user;

import java.util.List;

import com.lms.backend.shared.value.SharedUserRole;

// Work and open-order counts for the selected account.
public record AdminPreviewUserImpactResponse(
        Long userID,
        String username,
        SharedUserRole role,
        long adminPreviewUserImpactActivePickingTaskCount,
        List<String> adminPreviewUserImpactActivePickingOrderReferenceList,
        long adminPreviewUserImpactOpenPickupListCount,
        long adminPreviewUserImpactHeldPackageCount,
        List<String> adminPreviewUserImpactHeldPackageOrderReferenceList,
        long adminPreviewUserImpactOpenClientOrderCount,
        boolean adminPreviewUserImpactHasActiveWork) {
}
