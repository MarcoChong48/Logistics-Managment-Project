package com.lms.backend.shared.database;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped Refund class once available.
@Entity
@Table(name = "refund")
public class Refund {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    public Long refundID;

    @Column(name = "payment_id", nullable = false)
    public Long paymentID;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(name = "refund_status", nullable = false, length = 40)
    public String refundStatus;

    @Column(name = "provider_reference", length = 160)
    public String providerReference;

    @Column(name = "created_at", nullable = false)
    public Instant createdTime;

    @Column(name = "completed_at")
    public Instant completedTime;
}
