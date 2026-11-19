package com.lms.backend.shared.database;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped AppUser class once it is available.
@Entity
@Table(name = "app_user")
@DynamicUpdate
public class AppUser {

    // ===== SECTION 1: ACCOUNT FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    public Long userID;

    @Column(name = "username", nullable = false, length = 80)
    public String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    public SharedUserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    public SharedAccountStatus accountStatus;

    @Column(name = "failed_login_count", nullable = false)
    public int failedLoginCount;

    @Column(name = "lock_until")
    public Instant lockUntil;

    @Column(name = "must_change_password", nullable = false)
    public boolean mustChangePassword;

    @Column(name = "temp_password_expires_at")
    public Instant temporaryPasswordExpiryTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdTime;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedTime;

    @Column(name = "session_version", nullable = false)
    public int sessionVersion;
}
