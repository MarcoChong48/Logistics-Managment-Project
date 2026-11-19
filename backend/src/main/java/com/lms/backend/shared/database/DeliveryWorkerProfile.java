package com.lms.backend.shared.database;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped DeliveryWorkerProfile class once it is available.
@Entity
@Table(name = "delivery_worker_profile")
public class DeliveryWorkerProfile {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @Column(name = "user_id")
    public Long userID;

    @Column(name = "employee_number", length = 60)
    public String employeeNumber;

    @Column(name = "vehicle_type", length = 60)
    public String vehicleType;

    @Column(name = "vehicle_plate", length = 40)
    public String vehiclePlate;

    @Column(name = "equipment_note")
    public String equipmentNote;
}
