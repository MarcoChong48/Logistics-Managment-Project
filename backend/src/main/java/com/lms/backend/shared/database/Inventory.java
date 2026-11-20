package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapping when it becomes available.
@Entity
@Table(name = "inventory")
public class Inventory {

    // ===== SECTION 1: STORED FIELDS =====

    @Id
    @Column(name = "product_id")
    public Long productID;

    @Column(name = "physical_stock", nullable = false)
    public int physicalStock;

    @Column(name = "reserved_stock", nullable = false)
    public int reservedStock;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedTime;
}
