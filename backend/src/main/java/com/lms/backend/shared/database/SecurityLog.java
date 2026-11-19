package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped SecurityLog class once it is available.
@Entity
@Table(name = "security_log")
public class SecurityLog {

    // ===== SECTION 1: STORED EVENT FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "security_log_id")
    public Long securityLogID;

    @Column(name = "user_id")
    public Long userID;

    @Column(name = "event_type", nullable = false, length = 80)
    public String eventType;

    @Column(name = "event_time", nullable = false)
    public Instant timestamp;

    @Column(name = "result", nullable = false, length = 40)
    public String result;

    @Column(name = "ip_address", length = 45)
    public String ipAddress;

    @Column(name = "device_info", length = 300)
    public String deviceInfo;

    @Column(name = "details", length = 600)
    public String details;
}
