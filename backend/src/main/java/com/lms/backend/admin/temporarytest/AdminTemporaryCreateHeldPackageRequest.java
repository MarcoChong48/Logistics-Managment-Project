// TEMPORARY TEST CODE - remove before integration
// Stage 7 needs packages held by Delivery Workers in the two collectable sample statuses.
package com.lms.backend.admin.temporarytest;

import com.lms.backend.shared.value.SharedOrderStatus;

public record AdminTemporaryCreateHeldPackageRequest(
        String adminTemporaryCreateHeldPackageWorkerUsername,
        SharedOrderStatus adminTemporaryCreateHeldPackageOrderStatus) {
}
