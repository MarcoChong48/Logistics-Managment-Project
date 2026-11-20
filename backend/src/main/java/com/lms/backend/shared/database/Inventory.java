package com.lms.backend.shared.database;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    public Long productId;

    @Column(name = "physical_stock", nullable = false)
    public int physicalStock;

    @Column(name = "reserved_stock", nullable = false)
    public int reservedStock;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
