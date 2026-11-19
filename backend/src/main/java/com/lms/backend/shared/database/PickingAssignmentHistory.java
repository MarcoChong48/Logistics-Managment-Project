package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped PickingAssignmentHistory class once it is available.
@Entity
@Table(name = "picking_assignment_history")
public class PickingAssignmentHistory {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    public Long assignmentID;

    @Column(name = "picking_task_id", nullable = false)
    public Long pickingTaskID;

    @Column(name = "worker_user_id", nullable = false)
    public Long workerUserID;

    @Column(name = "assigned_at", nullable = false)
    public Instant assignedTime;

    @Column(name = "ended_at")
    public Instant endedTime;
}
