package com.lms.backend.shared.database;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Shared database placeholder. Use the team's mapped AuditLog class once it is available.
@Entity
@Table(name = "audit_log")
public class AuditLog {

    // ===== SECTION 1: STORED AUDIT FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    public Long auditID;

    @Column(name = "actor_user_id")
    public Long actorUserID;

    @Column(name = "action", nullable = false, length = 100)
    public String action;

    @Column(name = "target_type", nullable = false, length = 80)
    public String targetType;

    @Column(name = "target_id", nullable = false, length = 100)
    public String targetID;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    public Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    public Map<String, Object> newValue;

    @Column(name = "event_time", nullable = false)
    public Instant timestamp;

    @Column(name = "result", nullable = false, length = 40)
    public String result;
}
