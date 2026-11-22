package com.lms.backend.shared.notification;

public enum SharedNotificationType {

    // ===== SECTION 1: STAGE 6 MESSAGE TEMPLATES =====

    CLIENT_SHORTAGE_WAITING(
            "Order {orderReference} is delayed because an item is short in stock. We are waiting for new stock."),
    CLIENT_SHORTAGE_CANCELLED(
            "Order {orderReference} was cancelled because an item is not available. Your refund is being processed."),
    CLIENT_SHORTAGE_CANCELLED_REFUND_EXISTS("Order {orderReference} was cancelled because an item is not available. "
            + "A refund for this order had already been started."),
    CLIENT_SHORTAGE_CANCELLED_UNPAID("Order {orderReference} was cancelled because an item is not available. "
            + "No payment had been received for this order."),
    WORKER_PICKING_TASK_ASSIGNED("The picking task for order {orderReference} was assigned to you by an Admin. "
            + "Check the items against the saved progress before you continue."),
    WORKER_PICKING_TASK_REMOVED(
            "The picking task for order {orderReference} was moved to another worker by an Admin."),

    // ===== SECTION 2: STAGE 7 MESSAGE TEMPLATES =====

    CLIENT_REDELIVERY_APPROVED("Your redelivery request for order {orderReference} was approved. "
            + "The package will be sent out again."),
    CLIENT_CANCELLATION_APPROVED("Your cancellation request for order {orderReference} was approved. "
            + "The order is cancelled and your refund is being processed."),
    CLIENT_CANCELLATION_APPROVED_REFUND_EXISTS("Your cancellation request for order {orderReference} was approved. "
            + "The order is cancelled. A refund for this order had already been started."),
    CLIENT_CANCELLATION_APPROVED_UNPAID("Your cancellation request for order {orderReference} was approved. "
            + "The order is cancelled. No payment had been received for this order."),
    CLIENT_REQUEST_REJECTED("Your request for order {orderReference} was not approved. "
            + "Reason: {reason}. You can send a new request."),
    CLIENT_REQUEST_RECORDED("A {requestType} request for order {orderReference} was recorded for you by our staff."),
    CLIENT_DELIVERY_REMINDER("Order {orderReference} could not be delivered and is back at the warehouse. "
            + "Please choose redelivery or cancellation on the tracking page."),
    WORKER_DELIVERY_ASSIGNED("Delivery of order {orderReference} was assigned to you by an Admin. "
            + "Collect the package from the warehouse after it has been returned."),
    WORKER_DELIVERY_REMOVED("Delivery of order {orderReference} was moved to another worker by an Admin. "
            + "Please return the package to the warehouse.");

    private final String sharedFormatNotificationMessageTemplate;

    /**
     * What it does: Stores the notification text for one type.
     * Input: The template from the Admin plan.
     * Output: The type with its message template.
     */
    SharedNotificationType(String sharedFormatNotificationMessageTemplate) {
        this.sharedFormatNotificationMessageTemplate = sharedFormatNotificationMessageTemplate;
    }

    // ===== SECTION 3: FORMAT THE MESSAGE =====

    /**
     * What it does: Fills the order reference into a notification template.
     * Input: The order reference shown to the recipient.
     * Output: Message text for this notification type.
     */
    public String sharedFormatNotificationMessage(String orderReference) {
        return sharedFormatNotificationMessageTemplate.replace("{orderReference}", orderReference);
    }
}
