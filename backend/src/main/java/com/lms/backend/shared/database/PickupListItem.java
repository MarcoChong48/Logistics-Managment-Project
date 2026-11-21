package com.lms.backend.shared.database;

import java.time.Instant;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped PickupListItem class once available.
@Entity
@Table(name = "pickup_list_item")
@IdClass(PickupListItem.Key.class)
public class PickupListItem {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @Column(name = "pickup_list_id")
    public Long pickupListID;

    @Id
    @Column(name = "package_id")
    public Long packageID;

    @Column(name = "collection_outcome", nullable = false, length = 40)
    public String collectionOutcome;

    @Column(name = "collected_at")
    public Instant collectedTime;

    @Column(name = "cannot_collect_reason", length = 300)
    public String cannotCollectReason;

    // ===== SECTION 2: COMPOSITE KEY =====

    // The database identifies the row by both its list and its package.
    public record Key(Long pickupListID, Long packageID) implements Serializable {
    }
}
