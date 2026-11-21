package com.lms.backend.shared.value;

public enum SharedDeliveryFailureReason {
    RECIPIENT_UNAVAILABLE("The recipient was unavailable.", "CLIENT_NOT_HOME"),
    ADDRESS_NOT_FOUND("The address could not be located.", "WRONG_ADDRESS"),
    ACCESS_RESTRICTED("The delivery location could not be accessed."),
    DELIVERY_REFUSED("The delivery was declined at the address.", "CLIENT_REFUSED"),
    UNABLE_TO_CONTACT("The recipient could not be contacted."),
    OTHER("The delivery could not be completed.");

    private final String message;
    private final String deliveryAlias;

    SharedDeliveryFailureReason(String message) {
        this(message, null);
    }

    SharedDeliveryFailureReason(String message, String deliveryAlias) {
        this.message = message;
        this.deliveryAlias = deliveryAlias;
    }

    public static String messageForCode(String code) {
        if (code != null) {
            for (var reason : values()) {
                if (reason.name().equals(code) || code.equals(reason.deliveryAlias)) {
                    return reason.message;
                }
            }
        }
        return OTHER.message;
    }

    public static String approvedMessage(String text) {
        for (var reason : values()) {
            if (reason.message.equals(text)) {
                return reason.message;
            }
        }
        return OTHER.message;
    }
}
