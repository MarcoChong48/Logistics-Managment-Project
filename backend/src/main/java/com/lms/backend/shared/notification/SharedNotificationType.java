package com.lms.backend.shared.notification;

public enum SharedNotificationType {

    // ===== SECTION 1: STAGE 6 MESSAGE TEMPLATES =====

    CLIENT_SHORTAGE_WAITING(
            "Order {orderReference} is delayed because an item is short in stock. We are waiting for new stock."),
    CLIENT_SHORTAGE_CANCELLED(
            "Order {orderReference} was cancelled because an item is not available. Your refund is being processed."),
    CLIENT_SHORTAGE_CANCELLED_UNPAID("Order {orderReference} was cancelled because an item is not available. "
            + "No payment had been received for this order."),
    WORKER_PICKING_TASK_ASSIGNED("The picking task for order {orderReference} was assigned to you by an Admin. "
            + "Check the items against the saved progress before you continue."),
    WORKER_PICKING_TASK_REMOVED(
            "The picking task for order {orderReference} was moved to another worker by an Admin.");

    private final String sharedFormatNotificationMessageTemplate;

    /**
     * What it does: Stores the notification text for one type.
     * Input: The template from the Admin plan.
     * Output: The type with its message template.
     */
    SharedNotificationType(String sharedFormatNotificationMessageTemplate) {
        this.sharedFormatNotificationMessageTemplate = sharedFormatNotificationMessageTemplate;
    }

    // ===== SECTION 2: FORMAT THE MESSAGE =====

    /**
     * What it does: Fills the order reference into a notification template.
     * Input: The order reference shown to the recipient.
     * Output: Message text for this notification type.
     */
    public String sharedFormatNotificationMessage(String orderReference) {
        return sharedFormatNotificationMessageTemplate.replace("{orderReference}", orderReference);
    }
}
