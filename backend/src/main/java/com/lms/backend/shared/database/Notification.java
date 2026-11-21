package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped Notification class once available.
@Entity
@Table(name = "notification")
public class Notification {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    public Long notificationID;

    @Column(name = "recipient_user_id", nullable = false)
    public Long recipientUserID;

    @Column(name = "order_id")
    public Long orderID;

    @Column(name = "type", nullable = false, length = 60)
    public String type;

    @Column(name = "message", nullable = false, length = 600)
    public String message;

    @Column(name = "created_at", nullable = false)
    public Instant createdTime;

    @Column(name = "is_read", nullable = false)
    public boolean readStatus;
}
