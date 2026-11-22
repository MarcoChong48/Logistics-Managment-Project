package com.lms.backend.shared.database;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// Shared database placeholder. Use the team's mapped notification repository once available.
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * What it does: Finds when the most recent reminder was saved.
     * Input: The order ID and reminder notification type.
     * Output: Its creation time, or empty before a reminder is sent.
     */
    @Query("SELECT MAX(notification.createdTime) FROM Notification notification "
            + "WHERE notification.orderID = :orderID AND notification.type = :type")
    Optional<Instant> findLatestNotificationTime(Long orderID, String type);
}
