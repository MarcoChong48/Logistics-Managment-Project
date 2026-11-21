package com.lms.backend.shared.notification;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.Notification;
import com.lms.backend.shared.database.NotificationRepository;

@Service
public class SharedNotificationSender {

    // ===== SECTION 1: REPOSITORY THIS CLASS USES =====

    private final NotificationRepository sharedSendNotificationRepository;

    /**
     * What it does: Connects the notification sender to the shared database.
     * Input: The notification repository.
     * Output: A sender that can save unread notifications.
     */
    public SharedNotificationSender(NotificationRepository sharedSendNotificationRepository) {
        this.sharedSendNotificationRepository = sharedSendNotificationRepository;
    }

    // ===== SECTION 2: sharedSendNotification =====

    /**
     * What it does: Saves an unread notification alongside the change that triggered it.
     * Input: The recipient ID, optional order ID, notification type and public message.
     * Output: No web response. The caller's transaction saves the row.
     * Placeholder: PO-08 gives the recipient's module a message row to display.
     */
    @Transactional
    public void sharedSendNotification(
            long recipientUserID, Long orderID, SharedNotificationType type, String message) {
        // PLACEHOLDER OUTPUT PO-08
        // To: Client, Warehouse and Delivery.
        // What: An unread notification about an Admin decision.
        // How to use: Show rows for the signed-in recipient and allow marking them read.
        Notification sharedSendNotificationRow = new Notification();
        sharedSendNotificationRow.recipientUserID = recipientUserID;
        sharedSendNotificationRow.orderID = orderID;
        sharedSendNotificationRow.type = type.name();
        sharedSendNotificationRow.message = message;
        sharedSendNotificationRow.createdTime = Instant.now();
        sharedSendNotificationRow.readStatus = false;
        sharedSendNotificationRepository.save(sharedSendNotificationRow);
    }
}
