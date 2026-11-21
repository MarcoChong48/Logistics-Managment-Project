package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped StockMovement class once available.
@Entity
@Table(name = "stock_movement")
public class StockMovement {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    public Long movementID;

    @Column(name = "product_id", nullable = false)
    public Long productID;

    @Column(name = "package_id")
    public Long packageID;

    @Column(name = "custody_event_id")
    public Long custodyEventID;

    @Column(name = "actor_user_id")
    public Long actorUserID;

    @Column(name = "movement_type", nullable = false, length = 50)
    public String movementType;

    @Column(name = "quantity_delta", nullable = false)
    public int quantityDelta;

    @Column(name = "reserved_delta", nullable = false)
    public int reservedDelta;

    @Column(name = "event_time", nullable = false)
    public Instant eventTime;

    @Column(name = "reason", length = 300)
    public String reason;
}
