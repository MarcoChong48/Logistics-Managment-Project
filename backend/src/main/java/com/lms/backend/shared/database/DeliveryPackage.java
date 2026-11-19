package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped DeliveryPackage class once it is available.
@Entity
@Table(name = "delivery_package")
public class DeliveryPackage {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "package_id")
    public Long packageID;

    @Column(name = "order_id", nullable = false)
    public Long orderID;

    @Column(name = "package_qr_code", nullable = false, length = 160)
    public String packageQRCode;

    @Column(name = "created_at", nullable = false)
    public Instant createdTime;

    @Column(name = "custody_status", nullable = false, length = 40)
    public String custodyStatus;

    @Column(name = "current_handler_user_id")
    public Long currentHandlerUserID;
}
