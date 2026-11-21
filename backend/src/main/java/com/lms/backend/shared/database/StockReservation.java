package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped StockReservation class once available.
@Entity
@Table(name = "stock_reservation")
public class StockReservation {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    public Long reservationID;

    @Column(name = "order_item_id", nullable = false)
    public Long orderItemID;

    @Column(name = "payment_id")
    public Long paymentID;

    @Column(name = "quantity", nullable = false)
    public int quantity;

    @Column(name = "status", nullable = false, length = 30)
    public String status;

    @Column(name = "reserved_at", nullable = false)
    public Instant reservedAt;

    @Column(name = "released_at")
    public Instant releasedAt;

    @Column(name = "consumed_at")
    public Instant consumedAt;
}
