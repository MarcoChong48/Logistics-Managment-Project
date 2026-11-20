package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.lms.backend.shared.value.SharedScanResult;

// Shared database placeholder. Use the team's mapping when it becomes available.
@Entity
@Table(name = "picking_scan_history")
public class PickingScanHistory {

    // ===== SECTION 1: STORED FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scan_id")
    public Long scanID;

    @Column(name = "picking_task_id", nullable = false)
    public Long pickingTaskID;

    @Column(name = "order_item_id")
    public Long orderItemID;

    @Column(name = "worker_user_id", nullable = false)
    public Long workerUserID;

    @Column(name = "scanned_identifier", nullable = false, length = 160)
    public String scannedIdentifier;

    @Column(name = "scan_time", nullable = false)
    public Instant scanTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    public SharedScanResult result;

    @Column(name = "reason", length = 300)
    public String reason;

    @Column(name = "original_scan_id")
    public Long originalScanID;
}
