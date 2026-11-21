package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped ShortageCase class once available.
@Entity
@Table(name = "shortage_case")
public class ShortageCase {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shortage_case_id")
    public Long shortageCaseID;

    @Column(name = "order_id", nullable = false)
    public Long orderID;

    @Column(name = "product_id", nullable = false)
    public Long productID;

    @Column(name = "reported_by_worker_id", nullable = false)
    public Long reportedByWorkerID;

    @Column(name = "missing_quantity", nullable = false)
    public int missingQuantity;

    @Column(name = "report_note", length = 600)
    public String reportNote;

    @Column(name = "case_status", nullable = false, length = 40)
    public String caseStatus;

    @Column(name = "reported_at", nullable = false)
    public Instant reportedTime;

    @Column(name = "decision", length = 40)
    public String decision;

    @Column(name = "decided_by_admin_id")
    public Long decidedByAdminID;

    @Column(name = "decided_at")
    public Instant decidedTime;

    @Column(name = "decision_note", length = 300)
    public String decisionNote;

    @Column(name = "resolved_by_worker_id")
    public Long resolvedByWorkerID;

    @Column(name = "resolved_at")
    public Instant resolvedTime;
}
