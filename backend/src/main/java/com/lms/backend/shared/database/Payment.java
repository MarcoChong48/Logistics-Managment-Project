package com.lms.backend.shared.database;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped Payment class once available.
@Entity
@Table(name = "payment")
public class Payment {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    public Long paymentID;

    @Column(name = "order_id", nullable = false)
    public Long orderID;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(name = "payment_status", nullable = false, length = 40)
    public String paymentStatus;

    @Column(name = "provider_reference", length = 160)
    public String providerReference;

    @Column(name = "created_at", nullable = false)
    public Instant createdTime;

    @Column(name = "checkout_expires_at")
    public Instant checkoutExpiryTime;

    @Column(name = "ended_at")
    public Instant endedTime;

    @Column(name = "accepted_at")
    public Instant acceptedTime;
}
