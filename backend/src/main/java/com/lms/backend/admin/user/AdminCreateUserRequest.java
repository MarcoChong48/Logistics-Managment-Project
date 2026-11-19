package com.lms.backend.admin.user;

// New account username, role and optional Delivery Worker profile.
public record AdminCreateUserRequest(
        String adminCreateUserUsername,
        String adminCreateUserRole,
        String adminCreateUserEmployeeNumber,
        String adminCreateUserVehicleType,
        String adminCreateUserVehiclePlate,
        String adminCreateUserEquipmentNote) {
}
