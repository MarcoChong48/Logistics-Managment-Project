package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped OrderDeliveryDetail class once available.
// The private delivery columns are deliberately unmapped.
@Entity
@Table(name = "order_delivery_detail")
public class OrderDeliveryDetail {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_detail_id")
    public Long deliveryDetailID;

    @Column(name = "order_id", nullable = false)
    public Long orderID;

    @Column(name = "version_no", nullable = false)
    public int versionNo;

    @Column(name = "created_at", nullable = false)
    public Instant createdTime;
}
