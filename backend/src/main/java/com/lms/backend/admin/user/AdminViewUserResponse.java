package com.lms.backend.admin.user;

import java.time.Instant;

import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

// Account facts with Delivery Worker profile fields when present.
public record AdminViewUserResponse(
        Long userID,
        String username,
        SharedUserRole role,
        SharedAccountStatus accountStatus,
        int failedLoginCount,
        Instant lockUntil,
        boolean mustChangePassword,
        Instant temporaryPasswordExpiryTime,
        Instant createdTime,
        boolean adminViewUserIsLockedNow,
        boolean adminViewUserIsOwnAccount,
        String employeeNumber,
        String vehicleType,
        String vehiclePlate,
        String equipmentNote) {
}
