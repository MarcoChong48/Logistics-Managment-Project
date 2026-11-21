package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped DeliveryAttempt class once available.
@Entity
@Table(name = "delivery_attempt")
public class DeliveryAttempt {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_attempt_id")
    public Long deliveryAttemptID;

    @Column(name = "package_id", nullable = false)
    public Long packageID;

    @Column(name = "delivery_detail_id", nullable = false)
    public Long deliveryDetailID;

    @Column(name = "delivery_worker_user_id", nullable = false)
    public Long deliveryWorkerID;

    @Column(name = "pickup_custody_event_id")
    public Long pickupCustodyEventID;

    @Column(name = "start_time", nullable = false)
    public Instant startTime;

    @Column(name = "end_time")
    public Instant endTime;

    @Column(name = "outcome", nullable = false, length = 40)
    public String outcome;

    @Column(name = "failure_reason", length = 100)
    public String failureReason;

    @Column(name = "internal_note", length = 600)
    public String internalNote;
}
