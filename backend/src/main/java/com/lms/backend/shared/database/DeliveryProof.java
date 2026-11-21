package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped DeliveryProof class once available.
// The proof file reference is deliberately unmapped.
@Entity
@Table(name = "delivery_proof")
public class DeliveryProof {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proof_id")
    public Long proofID;

    @Column(name = "delivery_attempt_id", nullable = false)
    public Long deliveryAttemptID;

    @Column(name = "proof_type", nullable = false, length = 30)
    public String proofType;

    @Column(name = "created_at", nullable = false)
    public Instant createdTime;
}
