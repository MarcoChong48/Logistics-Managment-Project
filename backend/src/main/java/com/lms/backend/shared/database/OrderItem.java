package com.lms.backend.shared.database;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapping when it becomes available.
@Entity
@Table(name = "order_item")
public class OrderItem {

    // ===== SECTION 1: STORED FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    public Long orderItemID;

    @Column(name = "order_id", nullable = false)
    public Long orderID;

    @Column(name = "product_id", nullable = false)
    public Long productID;

    @Column(name = "quantity", nullable = false)
    public int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    public BigDecimal unitPrice;

    @Column(name = "picked_quantity", nullable = false)
    public int pickedQuantity;
}
