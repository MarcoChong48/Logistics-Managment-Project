package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped PickingTask class once it is available.
@Entity
@Table(name = "picking_task")
public class PickingTask {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "picking_task_id")
    public Long pickingTaskID;

    @Column(name = "order_id", nullable = false)
    public Long orderID;

    @Column(name = "task_status", nullable = false, length = 40)
    public String taskStatus;

    @Column(name = "start_time")
    public Instant startTime;

    @Column(name = "confirm_packing_time")
    public Instant confirmPackingTime;
}
