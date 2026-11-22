package com.lms.backend.shared.notification;

import com.lms.backend.shared.value.SharedDeliveryFailureReason;

public enum ClientNotificationType {
    PAYMENT_CONFIRMED("Payment was confirmed for your order."),
    SHIPPED("Your order was picked up for delivery."),
    DELIVERY_FAILED("The delivery could not be completed."),
    DELIVERED("Your order was delivered."),
    GENERAL("You have a notification.");

    private final String message;

    ClientNotificationType(String message) {
        this.message = message;
    }

    public static ClientNotificationType fromStored(String type) {
        for (var candidate : values()) {
            if (candidate.name().equals(type)) {
                return candidate;
            }
        }
        return GENERAL;
    }

    String messageForEvent(String failureCode) {
        return this == DELIVERY_FAILED ? SharedDeliveryFailureReason.messageForCode(failureCode) : message;
    }

    public String safeMessage(String storedMessage) {
        return this == DELIVERY_FAILED ? SharedDeliveryFailureReason.approvedMessage(storedMessage) : message;
    }
}
