package com.lms.backend.shared.database;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    public Long productId;

    @Column(name = "sku", nullable = false, unique = true, length = 60)
    public String sku;

    @Column(name = "name", nullable = false, length = 200)
    public String name;

    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    public BigDecimal currentPrice;

    @Column(name = "low_stock_threshold", nullable = false)
    public int lowStockThreshold;

    @Column(name = "listing_status", nullable = false, length = 30)
    public String listingStatus = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
