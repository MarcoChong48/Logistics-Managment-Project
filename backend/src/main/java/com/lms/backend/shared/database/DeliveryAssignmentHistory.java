package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped DeliveryAssignmentHistory class once available.
@Entity
@Table(name = "delivery_assignment_history")
public class DeliveryAssignmentHistory {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    public Long assignmentID;

    @Column(name = "package_id", nullable = false)
    public Long packageID;

    @Column(name = "previous_worker_user_id")
    public Long previousWorkerUserID;

    @Column(name = "new_worker_user_id", nullable = false)
    public Long newWorkerUserID;

    @Column(name = "assigned_by_admin_id", nullable = false)
    public Long assignedByAdminID;

    @Column(name = "assigned_at", nullable = false)
    public Instant assignedTime;

    @Column(name = "reason", nullable = false, length = 300)
    public String reason;
}
