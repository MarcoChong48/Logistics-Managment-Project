package com.lms.backend.client.notification;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/notifications")
class ClientNotificationController {

    private final ClientNotificationService notifications;

    ClientNotificationController(ClientNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    ResponseEntity<ClientNotificationResponse.Page> list(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cursor", required = false) Long cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notifications.list(limit, cursor));
    }

    @GetMapping("/unread-count")
    ResponseEntity<ClientNotificationResponse.UnreadCount> unreadCount() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notifications.unreadCount());
    }

    @PostMapping("/{notificationId}/read")
    ResponseEntity<ClientNotificationResponse.MarkReadResult> markRead(@PathVariable("notificationId") long notificationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notifications.markRead(notificationId));
    }

    @PostMapping("/read-all")
    ResponseEntity<ClientNotificationResponse.MarkAllResult> markAllRead() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notifications.markAllRead());
    }
}
