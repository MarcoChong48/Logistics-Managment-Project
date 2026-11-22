package com.lms.backend.client.notification;

import java.time.Instant;
import java.util.List;

public record ClientNotificationResponse(String notificationId, String type, String message, String orderReference,
        Instant createdAt, boolean isRead) {

    public record Page(List<ClientNotificationResponse> notifications, String nextCursor) {
    }

    public record UnreadCount(long unreadCount) {
    }

    public record MarkReadResult(String notificationId, boolean isRead) {
    }

    public record MarkAllResult(int markedRead) {
    }
}
