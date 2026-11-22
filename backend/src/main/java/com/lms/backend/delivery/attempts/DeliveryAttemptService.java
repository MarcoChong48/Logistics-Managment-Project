package com.lms.backend.delivery.attempts;

import static com.lms.backend.delivery.support.DeliveryValues.bad;
import static com.lms.backend.delivery.support.DeliveryValues.conflict;
import static com.lms.backend.delivery.support.DeliveryValues.denied;
import static com.lms.backend.delivery.support.DeliveryValues.fields;
import static com.lms.backend.delivery.support.DeliveryValues.id;
import static com.lms.backend.delivery.support.DeliveryValues.missing;
import static com.lms.backend.delivery.support.DeliveryValues.optional;
import static com.lms.backend.delivery.support.DeliveryValues.required;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.lms.backend.delivery.support.DeliveryCommands;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("delivery-full")
class DeliveryAttemptService {
    private static final Set<String> REASONS = Set.of(
            "CLIENT_NOT_HOME", "WRONG_ADDRESS", "CLIENT_REFUSED", "UNABLE_TO_CONTACT", "OTHER");

    private final JdbcClient db;
    private final DeliveryCommands commands;
    private final DeliveryProofStore proofStore;
    private final DeliveryRetentionStatus retention;

    DeliveryAttemptService(JdbcClient db, DeliveryCommands commands, DeliveryProofStore proofStore,
                           DeliveryRetentionStatus retention) {
        this.db = db;
        this.commands = commands;
        this.proofStore = proofStore;
        this.retention = retention;
    }

    Map<String, Object> start(long actor, long packageId, Map<String, Object> body) {
        return commands.run(actor, "START", Long.toString(packageId), body, () -> {
            var parcel = heldPackage(actor, packageId);
            if (!"SHIPPED".equals(parcel.status())) {
                throw conflict("Collect this package before starting delivery.");
            }
            var pickup = currentPickup(actor, packageId);
            if (activeAttempt(packageId).isPresent()) {
                throw conflict("A delivery attempt is already active.");
            }
            long detailId = frozenDetail(parcel, packageId);
            long attempt = db.sql("""
                    INSERT INTO delivery_attempt
                        (package_id, delivery_detail_id, delivery_worker_user_id, pickup_custody_event_id, delivery_managed)
                    VALUES (:parcel, :detail, :actor, :pickup, true)
                    RETURNING delivery_attempt_id
                    """)
                    .param("parcel", packageId).param("detail", detailId).param("actor", actor)
                    .param("pickup", pickup.id()).query(Long.class).single();
            setOrderStatus(parcel.orderId(), "OUT_FOR_DELIVERY");
            audit(actor, "START_DELIVERY", packageId, attempt, "IN_PROGRESS");
            return fields("message", "Delivery started for this order only.", "attemptId", Long.toString(attempt));
        });
    }

    Map<String, Object> outcome(long actor, long packageId, Map<String, Object> body) {
        return commands.run(actor, "OUTCOME", Long.toString(packageId), body, () -> {
            var parcel = heldPackage(actor, packageId);
            var pickup = currentPickup(actor, packageId);
            var attempt = checkedAttempt(actor, parcel, packageId, pickup);
            String choice = required(body, "outcome", 30);
            String message;
            switch (choice) {
                case "DELIVERED" -> {
                    markSelectedProofs(attempt.id(), body.get("proofIds"));
                    closeAttempt(attempt.id(), choice, null, null);
                    setOrderStatus(parcel.orderId(), "DELIVERED");
                    db.sql("""
                            UPDATE delivery_package
                            SET custody_status = 'RECIPIENT', current_handler_user_id = NULL
                            WHERE package_id = :parcel
                            """).param("parcel", packageId).update();
                    db.sql("""
                            INSERT INTO package_custody_event
                                (package_id, event_type, actor_user_id, pickup_list_id)
                            VALUES (:parcel, 'DELIVERED', :actor, :list)
                            """).param("parcel", packageId).param("actor", actor)
                            .param("list", pickup.listId(), Types.BIGINT).update();
                    notifyUsers(actor, parcel, "DELIVERED", "Delivery completed.");
                    message = "Delivery completed with saved proof.";
                }
                case "FAILED" -> {
                    String reason = required(body, "reason", 40);
                    String note = optional(body, "note", 600);
                    if (!REASONS.contains(reason)) throw bad("Choose a delivery failure reason.");
                    if ("OTHER".equals(reason) && note.isBlank()) throw bad("Add a note for Other.");
                    closeAttempt(attempt.id(), choice, reason, note.isBlank() ? null : note);
                    setOrderStatus(parcel.orderId(), "DELIVERY_FAILED");
                    notifyUsers(actor, parcel, "DELIVERY_FAILED", "Delivery failed: " + reason + ".");
                    message = "Failed attempt saved. Return the package for Warehouse confirmation.";
                }
                default -> throw bad("Choose Delivered or Failed.");
            }
            audit(actor, "DELIVERY_OUTCOME", packageId, attempt.id(), choice);
            return fields("message", message);
        });
    }

    Map<String, Object> upload(long actor, long packageId, String type, MultipartFile file, String requestId) {
        if (!"SIGNATURE".equals(type) && !"PHOTO".equals(type)) throw bad("Choose Photo or Signature.");
        if (file.isEmpty() || file.getSize() > DeliveryProofStore.MAX_BYTES) {
            throw bad("Choose a non-empty image no larger than 5 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw bad("The image could not be read. Try again.");
        }
        return commands.run(actor, "PROOF", Long.toString(packageId),
                fields("requestId", requestId, "type", type, "sha256", digest(bytes)),
                () -> saveProof(actor, packageId, type, bytes));
    }

    private Map<String, Object> saveProof(long actor, long packageId, String type, byte[] bytes) {
        var parcel = heldPackage(actor, packageId);
        var attempt = checkedAttempt(actor, parcel, packageId, currentPickup(actor, packageId));
        long count = db.sql("SELECT COUNT(*) FROM delivery_proof WHERE delivery_attempt_id = :attempt")
                .param("attempt", attempt.id()).query(Long.class).single();
        if (count >= 10) throw bad("This attempt already has enough saved images. Use a saved proof.");
        String reference = proofStore.save(bytes, type);
        long proofId = db.sql("""
                INSERT INTO delivery_proof (delivery_attempt_id, proof_type, proof_reference)
                VALUES (:attempt, :type, :reference)
                RETURNING proof_id
                """).param("attempt", attempt.id()).param("type", type).param("reference", reference)
                .query(Long.class).single();
        String id = Long.toString(proofId);
        return fields("id", id, "type", type, "url", "/api/delivery/proofs/" + id, "message", "Proof saved.");
    }

    @Transactional
    public DeliveryProofStore.ProofContent readProof(long actor, long proofId) {
        commands.lock();
        var proof = db.sql("""
                SELECT pa.package_id, pa.delivery_attempt_id, pa.delivery_worker_user_id, pa.outcome,
                    pp.proof_reference
                FROM delivery_proof pp
                JOIN delivery_attempt pa ON pa.delivery_attempt_id = pp.delivery_attempt_id
                WHERE pp.proof_id = :proof
                """).param("proof", proofId).query((rs, row) -> new ProofAccess(
                rs.getLong("package_id"), rs.getLong("delivery_attempt_id"), rs.getLong("delivery_worker_user_id"),
                rs.getString("outcome"), rs.getString("proof_reference")))
                .optional().orElseThrow(() -> missing("Proof not found."));
        if (proof.workerId() != actor || !"IN_PROGRESS".equals(proof.outcome())) throw denied();
        var parcel = heldPackage(actor, proof.packageId());
        var attempt = checkedAttempt(actor, parcel, proof.packageId(), currentPickup(actor, proof.packageId()));
        if (attempt.id() != proof.attemptId()) throw denied();
        return proofStore.read(proof.reference());
    }

    private PackageState heldPackage(long actor, long packageId) {
        var parcel = db.sql("""
                SELECT p.order_id, p.current_handler_user_id, p.custody_status, p.delivery_detail_id,
                    p.delivery_hold_reason, o.order_status, o.client_user_id
                FROM delivery_package p
                JOIN customer_order o ON o.order_id = p.order_id
                WHERE p.package_id = :parcel
                FOR UPDATE OF p, o
                """).param("parcel", packageId).query((rs, row) -> new PackageState(
                rs.getLong("order_id"), rs.getLong("client_user_id"), rs.getObject("current_handler_user_id", Long.class),
                rs.getString("custody_status"), rs.getObject("delivery_detail_id", Long.class),
                rs.getString("delivery_hold_reason"), rs.getString("order_status")))
                .optional().orElseThrow(() -> missing("Package not found."));
        if (!"DELIVERY".equals(parcel.custody()) || !Objects.equals(parcel.holderId(), actor)) throw denied();
        if (parcel.holdReason() != null && !parcel.holdReason().isBlank()) {
            throw conflict("Resolve the package hold before changing this delivery.");
        }
        return parcel;
    }

    private Pickup currentPickup(long actor, long packageId) {
        var pickup = db.sql("""
                SELECT e.custody_event_id, e.event_type, e.actor_user_id, e.pickup_list_id,
                    assignment.new_worker_user_id
                FROM package_custody_event e
                LEFT JOIN LATERAL (
                    SELECT new_worker_user_id
                    FROM delivery_assignment_history h
                    WHERE h.package_id = e.package_id AND h.assigned_at >= e.event_time
                    ORDER BY h.assigned_at DESC, h.assignment_id DESC
                    LIMIT 1
                ) assignment ON TRUE
                WHERE e.package_id = :parcel AND e.event_type IN
                    ('PICKED_UP', 'RETURN_REQUESTED', 'RETURN_RECEIVED', 'DELIVERED')
                ORDER BY e.event_time DESC, e.custody_event_id DESC
                LIMIT 1
                """).param("parcel", packageId).query((rs, row) -> new Pickup(
                rs.getLong("custody_event_id"), rs.getObject("pickup_list_id", Long.class), rs.getString("event_type"),
                rs.getObject("actor_user_id", Long.class), rs.getObject("new_worker_user_id", Long.class)))
                .optional().orElseThrow(() -> conflict("This package has no current pickup."));
        if (!"PICKED_UP".equals(pickup.event())) {
            throw conflict("This package has no current pickup.");
        }
        Long worker = pickup.assignedWorker() == null ? pickup.originalWorker() : pickup.assignedWorker();
        if (!Objects.equals(worker, actor)) throw denied();
        return pickup;
    }


    private long frozenDetail(PackageState parcel, long packageId) {
        if (parcel.detailId() != null) {
            return db.sql("""
                    SELECT delivery_detail_id FROM order_delivery_detail
                    WHERE order_id = :orderId AND delivery_detail_id = :detail
                    """).param("orderId", parcel.orderId()).param("detail", parcel.detailId())
                    .query(Long.class).optional().orElseThrow(() -> conflict("The delivery detail is invalid."));
        }
        // Packages collected by older code may have no saved address version.
        long detail = db.sql("""
                SELECT delivery_detail_id FROM order_delivery_detail
                WHERE order_id = :orderId ORDER BY version_no DESC LIMIT 1
                """).param("orderId", parcel.orderId()).query(Long.class).optional()
                .orElseThrow(() -> conflict("The order has no delivery detail."));
        db.sql("UPDATE delivery_package SET delivery_detail_id = :detail WHERE package_id = :parcel")
                .param("detail", detail).param("parcel", packageId).update();
        return detail;
    }

    private java.util.Optional<Attempt> activeAttempt(long packageId) {
        return db.sql("""
                SELECT delivery_attempt_id, delivery_worker_user_id, pickup_custody_event_id, delivery_detail_id
                FROM delivery_attempt WHERE package_id = :parcel AND outcome = 'IN_PROGRESS'
                """).param("parcel", packageId).query((rs, row) -> new Attempt(
                rs.getLong("delivery_attempt_id"), rs.getLong("delivery_worker_user_id"),
                rs.getObject("pickup_custody_event_id", Long.class), rs.getLong("delivery_detail_id"))).optional();
    }

    private Attempt checkedAttempt(long actor, PackageState parcel, long packageId, Pickup pickup) {
        if (!"OUT_FOR_DELIVERY".equals(parcel.status())) {
            throw conflict("Start the current delivery before submitting proof or an outcome.");
        }
        var attempt = activeAttempt(packageId)
                .orElseThrow(() -> conflict("No current delivery attempt is active."));
        if (attempt.workerId() != actor) throw denied();
        if (!Objects.equals(attempt.pickupId(), pickup.id())
                || attempt.detailId() != frozenDetail(parcel, packageId)) {
            throw conflict("This attempt does not match the current pickup and delivery detail.");
        }
        return attempt;
    }

    private void markSelectedProofs(long attemptId, Object input) {
        if (!(input instanceof List<?> items) || items.isEmpty() || items.size() > 10) {
            throw bad("Select at least one saved proof.");
        }
        Set<Long> selected = new HashSet<>();
        for (Object item : items) {
            if (!(item instanceof String value) || !selected.add(id(value))) {
                throw bad("Select a valid set of saved proofs.");
            }
        }
        var proofs = db.sql("""
                SELECT proof_id, proof_reference FROM delivery_proof
                WHERE delivery_attempt_id = :attempt AND proof_id IN (:ids)
                """).param("attempt", attemptId).param("ids", selected)
                .query((rs, row) -> new Proof(rs.getLong("proof_id"), rs.getString("proof_reference"))).list();
        if (proofs.size() != selected.size() || proofs.stream().anyMatch(proof -> !proofStore.exists(proof.reference()))) {
            throw bad("A selected proof is unavailable. Save a new proof before completing delivery.");
        }
        db.sql("""
                UPDATE delivery_proof SET delivery_used_for_outcome = TRUE
                WHERE delivery_attempt_id = :attempt AND proof_id IN (:ids)
                """).param("attempt", attemptId).param("ids", selected).update();
    }

    private void setOrderStatus(long orderId, String status) {
        db.sql("UPDATE customer_order SET order_status = :status WHERE order_id = :orderId")
                .param("status", status).param("orderId", orderId).update();
    }

    private void closeAttempt(long attemptId, String outcome, String reason, String note) {
        db.sql("""
                UPDATE delivery_attempt
                SET outcome = :outcome, end_time = NOW(), failure_reason = :reason, internal_note = :note
                WHERE delivery_attempt_id = :attempt
                """).param("attempt", attemptId).param("outcome", outcome)
                .param("reason", reason, Types.VARCHAR).param("note", note, Types.VARCHAR).update();
    }

    private void notifyUsers(long actor, PackageState parcel, String type, String message) {
        db.sql("""
                INSERT INTO notification (recipient_user_id, order_id, type, message)
                SELECT DISTINCT recipient, :orderId, :type, :message
                FROM (VALUES (CAST(:worker AS BIGINT)), (CAST(:client AS BIGINT))) recipients(recipient)
                """).param("worker", actor).param("client", parcel.clientId()).param("orderId", parcel.orderId())
                .param("type", type).param("message", message).update();
    }

    private void audit(long actor, String action, long packageId, long attemptId, String outcome) {
        db.sql("""
                INSERT INTO audit_log (actor_user_id, action, target_type, target_id, new_value, result)
                VALUES (:actor, :action, 'PACKAGE', :target,
                    jsonb_build_object('attemptId', CAST(:attempt AS TEXT), 'outcome', CAST(:outcome AS TEXT)),
                    'SUCCESS')
                """).param("actor", actor).param("action", action).param("target", Long.toString(packageId))
                .param("attempt", Long.toString(attemptId)).param("outcome", outcome).update();
    }

    @Scheduled(initialDelayString = "${lms.delivery.maintenance-delay-ms:3600000}",
            fixedDelayString = "${lms.delivery.maintenance-delay-ms:3600000}")
    @Transactional
    public void retainCompletedAttempts() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                retention.finish(status == STATUS_COMMITTED);
            }
        });
        commands.lock();
        // Only remove orphaned files left by an interrupted upload.
        proofStore.removeOrphans(reference -> db.sql("""
                SELECT EXISTS (SELECT 1 FROM delivery_proof WHERE proof_reference = :reference)
                """).param("reference", reference).query(Boolean.class).single());
        var expired = db.sql("""
                SELECT delivery_attempt_id FROM delivery_attempt
                WHERE delivery_managed AND end_time < NOW() - INTERVAL '365 days' AND outcome IN ('DELIVERED', 'FAILED')
                ORDER BY end_time
                """).query(Long.class).list();
        for (long attempt : expired) {
            var references = db.sql("""
                    SELECT proof_reference FROM delivery_proof WHERE delivery_attempt_id = :attempt
                    """).param("attempt", attempt).query(String.class).list();
            // A different module owns proof references without the Delivery prefix.
            if (!references.stream().allMatch(proofStore::owns)) continue;
            db.sql("""
                    INSERT INTO delivery_retained_attempt (package_id, worker_id, outcome, event_at)
                    SELECT package_id, delivery_worker_user_id, outcome, end_time
                    FROM delivery_attempt WHERE delivery_attempt_id = :attempt
                    """).param("attempt", attempt).update();
            db.sql("DELETE FROM delivery_proof WHERE delivery_attempt_id = :attempt")
                    .param("attempt", attempt).update();
            db.sql("DELETE FROM delivery_attempt WHERE delivery_attempt_id = :attempt")
                    .param("attempt", attempt).update();
            proofStore.deleteAfterCommit(references);
        }
        db.sql("DELETE FROM delivery_retained_attempt WHERE event_at < NOW() - INTERVAL '1095 days'").update();
    }

    private String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private record PackageState(long orderId, long clientId, Long holderId, String custody, Long detailId,
            String holdReason, String status) {}
    private record Pickup(long id, Long listId, String event, Long originalWorker, Long assignedWorker) {}
    private record Attempt(long id, long workerId, Long pickupId, long detailId) {}
    private record Proof(long id, String reference) {}
    private record ProofAccess(long packageId, long attemptId, long workerId, String outcome, String reference) {}
}
