package com.lms.backend.admin.user;

// Editable Delivery Worker profile fields.
public record AdminEditDeliveryWorkerProfileRequest(
        String adminEditDeliveryWorkerProfileEmployeeNumber,
        String adminEditDeliveryWorkerProfileVehicleType,
        String adminEditDeliveryWorkerProfileVehiclePlate,
        String adminEditDeliveryWorkerProfileEquipmentNote) {
}
