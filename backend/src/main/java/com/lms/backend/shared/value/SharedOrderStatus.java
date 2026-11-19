package com.lms.backend.shared.value;

// The confirmed main statuses of an order.
public enum SharedOrderStatus {
    AWAITING_PAYMENT,
    READY_FOR_PICKING,
    PICKING,
    READY_FOR_PICKUP,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    READY_FOR_REDELIVERY,
    DELIVERED,
    CANCELLED
}
