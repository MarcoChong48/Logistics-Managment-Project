package com.lms.backend.shared.database;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped PackageCustodyEvent class once available.
// Free-form custody details are deliberately unmapped.
@Entity
@Table(name = "package_custody_event")
public class PackageCustodyEvent {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "custody_event_id")
    public Long custodyEventID;

    @Column(name = "package_id", nullable = false)
    public Long packageID;

    @Column(name = "event_type", nullable = false, length = 50)
    public String eventType;

    @Column(name = "actor_user_id")
    public Long actorUserID;

    @Column(name = "pickup_list_id")
    public Long pickupListID;

    @Column(name = "event_time", nullable = false)
    public Instant eventTime;
}
