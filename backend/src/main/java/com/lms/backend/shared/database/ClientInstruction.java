package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped ClientInstruction class once available.
@Entity
@Table(name = "client_instruction")
public class ClientInstruction {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instruction_id")
    public Long instructionID;

    @Column(name = "order_id", nullable = false)
    public Long orderID;

    @Column(name = "client_user_id", nullable = false)
    public Long clientUserID;

    @Column(name = "delivery_detail_id")
    public Long deliveryDetailID;

    @Column(name = "return_custody_event_id")
    public Long returnCustodyEventID;

    @Column(name = "instruction_type", nullable = false, length = 40)
    public String instructionType;

    @Column(name = "status", nullable = false, length = 40)
    public String status;

    @Column(name = "submitted_at", nullable = false)
    public Instant submittedTime;

    @Column(name = "resolved_by_admin_id")
    public Long resolvedByAdminID;

    @Column(name = "resolved_at")
    public Instant resolvedTime;

    @Column(name = "source", nullable = false, length = 30)
    public String source;

    @Column(name = "recorded_by_user_id")
    public Long recordedByUserID;

    @Column(name = "resolution_note", length = 300)
    public String resolutionNote;
}
