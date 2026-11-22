package com.lms.backend.client.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lms.backend.shared.notification.ClientNotificationType;

@Repository
class ClientNotificationRepository {

    private final JdbcClient database;

    ClientNotificationRepository(JdbcClient database) {
        this.database = database;
    }

    Optional<OffsetDateTime> findOwnedCreationTime(long clientId, long notificationId) {
        return database.sql("""
                SELECT created_at FROM notification
                WHERE recipient_user_id = :clientId AND notification_id = :notificationId
                """).param("clientId", clientId).param("notificationId", notificationId)
                .query(OffsetDateTime.class).optional();
    }

    List<ClientNotificationResponse> findPage(long clientId, Long cursor, OffsetDateTime cursorTime, int pageSize) {
        String cursorFilter = cursor == null ? "" : "AND (n.created_at, n.notification_id) < (:cursorTime, :cursor)";
        var query = database.sql("""
                SELECT n.notification_id,
                    CASE WHEN n.order_id IS NULL OR o.order_id IS NOT NULL THEN n.type ELSE 'GENERAL' END AS type,
                    n.message, o.order_reference, n.created_at, n.is_read
                FROM notification n LEFT JOIN customer_order o
                    ON o.order_id = n.order_id AND o.client_user_id = n.recipient_user_id
                WHERE n.recipient_user_id = :clientId %s
                ORDER BY n.created_at DESC, n.notification_id DESC LIMIT :pageSize
                """.formatted(cursorFilter)).param("clientId", clientId).param("pageSize", pageSize);
        if (cursor != null) {
            query.param("cursorTime", cursorTime).param("cursor", cursor);
        }
        return query.query((row, index) -> {
            var type = ClientNotificationType.fromStored(row.getString("type"));
            return new ClientNotificationResponse(Long.toString(row.getLong("notification_id")), type.name(),
                    type.safeMessage(row.getString("message")), row.getString("order_reference"),
                    row.getObject("created_at", OffsetDateTime.class).toInstant(), row.getBoolean("is_read"));
        }).list();
    }

    long unreadCount(long clientId) {
        return database.sql("SELECT COUNT(*) FROM notification WHERE recipient_user_id = :clientId AND NOT is_read")
                .param("clientId", clientId).query(Long.class).single();
    }

    int markRead(long clientId, long notificationId) {
        return database.sql("""
                UPDATE notification SET is_read = TRUE
                WHERE recipient_user_id = :clientId AND notification_id = :notificationId AND NOT is_read
                """).param("clientId", clientId).param("notificationId", notificationId).update();
    }

    int markAllRead(long clientId) {
        return database.sql("UPDATE notification SET is_read = TRUE WHERE recipient_user_id = :clientId AND NOT is_read")
                .param("clientId", clientId).update();
    }
}
