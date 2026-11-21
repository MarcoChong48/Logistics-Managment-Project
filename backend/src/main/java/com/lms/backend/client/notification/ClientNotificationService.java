package com.lms.backend.client.notification;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUserReader;

@Service
class ClientNotificationService {

    private final ClientNotificationRepository notifications;
    private final SharedCurrentUserReader currentUser;

    ClientNotificationService(ClientNotificationRepository notifications, SharedCurrentUserReader currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ClientNotificationResponse.Page list(int limit, Long cursor) {
        if (limit < 1 || limit > 50) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "CLIENT_NOTIFICATION_PAGE_SIZE_INVALID",
                    "Choose a limit between 1 and 50.");
        }
        long clientId = currentUser.sharedReadCurrentUser().userID();
        var cursorTime = cursor == null ? null : notifications.findOwnedCreationTime(clientId, cursor)
                .orElseThrow(this::notFound);
        var rows = notifications.findPage(clientId, cursor, cursorTime, limit + 1);
        var page = rows.stream().limit(limit).toList();
        String nextCursor = rows.size() > limit ? page.getLast().notificationId() : null;
        return new ClientNotificationResponse.Page(page, nextCursor);
    }

    @Transactional(readOnly = true)
    public ClientNotificationResponse.UnreadCount unreadCount() {
        return new ClientNotificationResponse.UnreadCount(
                notifications.unreadCount(currentUser.sharedReadCurrentUser().userID()));
    }

    @Transactional
    public ClientNotificationResponse.MarkReadResult markRead(long notificationId) {
        long clientId = currentUser.sharedReadCurrentUser().userID();
        if (notifications.markRead(clientId, notificationId) == 0
                && notifications.findOwnedCreationTime(clientId, notificationId).isEmpty()) {
            throw notFound();
        }
        return new ClientNotificationResponse.MarkReadResult(Long.toString(notificationId), true);
    }

    @Transactional
    public ClientNotificationResponse.MarkAllResult markAllRead() {
        return new ClientNotificationResponse.MarkAllResult(
                notifications.markAllRead(currentUser.sharedReadCurrentUser().userID()));
    }

    private SharedRuleException notFound() {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "CLIENT_NOTIFICATION_NOT_FOUND", "Notification was not found.");
    }
}
