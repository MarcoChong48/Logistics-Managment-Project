package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped PickupList class once it is available.
@Entity
@Table(name = "pickup_list")
public class PickupList {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pickup_list_id")
    public Long pickupListID;

    @Column(name = "list_qr_code", nullable = false, length = 160)
    public String listQRCode;

    @Column(name = "created_by_worker_id", nullable = false)
    public Long createdByWorkerID;

    @Column(name = "created_at", nullable = false)
    public Instant createdTime;

    @Column(name = "status", nullable = false, length = 40)
    public String status;

    @Column(name = "claimed_by_delivery_user_id")
    public Long claimedByDeliveryWorkerID;

    @Column(name = "claimed_at")
    public Instant claimedTime;
}
