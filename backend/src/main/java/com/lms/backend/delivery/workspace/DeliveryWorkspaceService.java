package com.lms.backend.delivery.workspace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUser;
import com.lms.backend.shared.value.SharedUserRole;

@Service
public class DeliveryWorkspaceService {

    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private static final Set<String> IN_TRANSIT = Set.of("SHIPPED", "OUT_FOR_DELIVERY");
    private final JdbcTemplate database;
    private final DeliveryFullWorkspaceData full;

    DeliveryWorkspaceService(JdbcTemplate database, ObjectProvider<DeliveryFullWorkspaceData> fullMode) {
        this.database = database;
        this.full = fullMode.getIfAvailable();
    }

    @Transactional
    public Map<String, Object> readWorkspace(SharedCurrentUser user) {
        if (user.role() != SharedUserRole.DELIVERY_WORKER) {
            throw new SharedRuleException(HttpStatus.FORBIDDEN, "DELIVERY_FORBIDDEN",
                    "This page is for Delivery Workers.");
        }
        long workerId = user.userID();
        if (full != null) full.refreshAssignments();
        var pickupLists = pickupLists(workerId);
        var tasks = activeTasks(workerId);
        var attempts = attempts(workerId, user.username());
        var returns = full == null ? List.<Map<String,Object>>of() : full.returns(workerId);
        var reviews = full == null ? List.<Map<String,Object>>of() : full.reviews(workerId, pickupLists, returns);
        boolean enabled = full != null;
        return fields(
                "user", fields("id", Long.toString(workerId), "username", user.username(),
                        "displayName", user.username(), "role", user.role().name()),
                "mode", "integration",
                "capabilities", fields("pickup", enabled, "attempts", enabled, "returns", enabled, "proofs", enabled),
                "stats", stats(workerId, tasks, pickupLists),
                "pickupLists", pickupLists, "tasks", tasks, "attempts", attempts,
                "returns", returns, "notifications", notifications(workerId),
                "reviews", reviews, "reviewQueue", reviews,
                "retainedSummaries", enabled ? full.retained(workerId) : List.of(),
                "decisions", DeliveryDecisions.items(enabled),
                "settings", fields("photoLimitBytes", 5242880, "staleClaimMinutes", 30,
                        "reviewFailureCount", 2, "reminderDays", 3, "finalReviewDays", 7),
                "storageHealth", enabled ? full.storageHealth() : null,
                "integrations", List.of(
                        fields("id", "auth", "name", "Shared sign-in", "status", "CONNECTED",
                                "detail", "Delivery uses the shared LMS session and role checks."),
                        fields("id", "database", "name", "Shared PostgreSQL", "status", "CONNECTED",
                                "detail", enabled ? "Delivery saves its workflow in the shared tables."
                                        : "This workspace reads your records from the shared database."),
                        fields("id", "workflow", "name", "Warehouse, Client and Admin handoffs", "status", "PLACE_HOLDER",
                                "detail", enabled ? "Place holder: connect the handoff through the responsible role."
                                        : "Place holder: full Delivery actions use the local extension.")));
    }

    private List<Map<String, Object>> pickupLists(long workerId) {
        String assignment = full == null ? "NULL::bigint AS assigned, false AS stale"
                : "assigned_to_delivery_user_id AS assigned, claimed_by_delivery_user_id IS NOT NULL "
                    + "AND status <> 'CLOSED' AND COALESCE(delivery_last_activity_at, claimed_at) < now() - interval '30 minutes' AS stale";
        String scope = full == null ? "" : " OR (status = 'OPEN' AND claimed_by_delivery_user_id IS NULL AND assigned_to_delivery_user_id = ?)";
        return database.query("""
                SELECT pickup_list_id, list_qr_code, status, created_at,
                       claimed_by_delivery_user_id, claimed_at, %s
                FROM public.pickup_list
                WHERE claimed_by_delivery_user_id = ?%s
                ORDER BY created_at DESC, pickup_list_id
                """.formatted(assignment, scope), (row, index) -> {
            String status = row.getString("status");
            // The shared database stores confirmed lists as OPEN with a claimant.
            if ("OPEN".equals(status) && row.getString("claimed_by_delivery_user_id") != null) {
                status = "CLAIMED";
            }
            return fields("id", row.getString("pickup_list_id"), "qrCode", row.getString("list_qr_code"),
                    "status", status, "createdAt", time(row, "created_at"), "assignedTo", row.getString("assigned"),
                    "claimedBy", row.getString("claimed_by_delivery_user_id"), "claimedAt", time(row, "claimed_at"),
                    "stale", row.getBoolean("stale"), "packages", listPackages(row.getLong("pickup_list_id")));
        }, full == null ? new Object[]{workerId} : new Object[]{workerId, workerId});
    }

    private List<Map<String, Object>> listPackages(long listId) {
        return database.query("""
                SELECT p.package_id, p.package_qr_code, orders.order_reference, orders.order_status,
                       list_item.collection_outcome, list_item.cannot_collect_reason, %s AS hold_reason,
                       (SELECT string_agg(item.quantity || ' × ' || product.name, ', ' ORDER BY item.order_item_id)
                        FROM public.order_item item JOIN public.product product USING (product_id)
                        WHERE item.order_id = orders.order_id) AS item_summary
                FROM public.pickup_list_item list_item
                JOIN public.delivery_package p USING (package_id)
                JOIN public.customer_order orders ON orders.order_id = p.order_id
                WHERE list_item.pickup_list_id = ?
                ORDER BY p.package_id
                """.formatted(full == null ? "NULL::text" : "p.delivery_hold_reason"), (row, index) -> fields(
                "id", row.getString("package_id"), "qrCode", row.getString("package_qr_code"),
                "orderReference", row.getString("order_reference"), "status", row.getString("order_status"),
                "itemSummary", row.getString("item_summary"),
                "collectionOutcome", row.getString("collection_outcome"),
                "cannotCollectReason", row.getString("cannot_collect_reason"), "holdReason", row.getString("hold_reason")), listId);
    }

    private List<Map<String, Object>> activeTasks(long workerId) {
        String snapshot = full == null
                ? "CASE WHEN orders.order_status='SHIPPED' THEN NULL ELSE latest_attempt.delivery_detail_id END"
                : "CASE WHEN orders.order_status='SHIPPED' THEN p.delivery_detail_id "
                    + "ELSE COALESCE(latest_attempt.delivery_detail_id, p.delivery_detail_id) END";
        String retainedCount = full == null ? "" : """
                + (SELECT COUNT(*) FROM public.delivery_retained_attempt retained
                   WHERE retained.package_id = p.package_id)
                """;
        return database.query("""
                SELECT p.package_id, p.package_qr_code, p.custody_status, orders.order_id,
                       orders.order_reference, orders.order_status, details.recipient_name,
                       details.phone, details.address_text,
                       CASE WHEN latest_attempt.outcome = 'IN_PROGRESS' THEN latest_attempt.delivery_attempt_id
                            ELSE NULL END AS active_attempt_id,
                       (SELECT COUNT(*) FROM public.delivery_attempt previous
                        WHERE previous.package_id = p.package_id)%s AS attempt_count,
                       COALESCE(return_event.event_type = 'RETURN_REQUESTED', false) AS return_pending,
                       p.current_handler_user_id = COALESCE(assigned.new_worker_user_id, p.current_handler_user_id) AS responsible
                FROM public.delivery_package p
                JOIN public.customer_order orders ON orders.order_id = p.order_id
                LEFT JOIN LATERAL (
                    SELECT new_worker_user_id FROM public.delivery_assignment_history
                    WHERE package_id = p.package_id AND assigned_at >= COALESCE((
                        SELECT MAX(event_time) FROM public.package_custody_event
                        WHERE package_id=p.package_id AND event_type='PICKED_UP'
                    ), '-infinity'::timestamptz)
                    ORDER BY assigned_at DESC, assignment_id DESC LIMIT 1
                ) assigned ON true
                LEFT JOIN LATERAL (
                    SELECT delivery_attempt_id, delivery_detail_id, outcome FROM public.delivery_attempt
                    WHERE package_id = p.package_id AND delivery_worker_user_id = p.current_handler_user_id
                    ORDER BY start_time DESC, delivery_attempt_id DESC LIMIT 1
                ) latest_attempt ON true
                LEFT JOIN LATERAL (
                    SELECT recipient_name, phone, address_text FROM public.order_delivery_detail detail
                    WHERE detail.order_id = orders.order_id
                      AND p.current_handler_user_id = COALESCE(assigned.new_worker_user_id, p.current_handler_user_id)
                      AND (detail.delivery_detail_id = %s OR (%s IS NULL AND orders.order_status = 'SHIPPED'))
                    ORDER BY version_no DESC, delivery_detail_id DESC LIMIT 1
                ) details ON true
                LEFT JOIN LATERAL (
                    SELECT event_type FROM public.package_custody_event
                    WHERE package_id = p.package_id AND event_type IN ('PICKED_UP', 'RETURN_REQUESTED', 'RETURN_RECEIVED')
                    ORDER BY event_time DESC, custody_event_id DESC LIMIT 1
                ) return_event ON true
                WHERE p.current_handler_user_id = ? AND p.custody_status = 'DELIVERY'
                  AND orders.order_status IN ('SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERY_FAILED')
                ORDER BY p.package_id
                """.formatted(retainedCount, snapshot, snapshot), (row, index) -> {
            String status = row.getString("order_status");
            boolean pending = row.getBoolean("return_pending");
            boolean actionAllowed = full != null && row.getBoolean("responsible") && !pending;
            String attempt = row.getString("active_attempt_id");
            boolean record = actionAllowed && attempt != null && "OUT_FOR_DELIVERY".equals(status);
            return fields(
                    "id", row.getString("package_id"), "qrCode", row.getString("package_qr_code"),
                    "orderReference", row.getString("order_reference"), "status", status,
                    "custody", row.getString("custody_status"), "recipientName", row.getString("recipient_name"),
                    "phone", row.getString("phone"), "address", row.getString("address_text"),
                    "products", orderProducts(row.getLong("order_id")), "attemptCount", row.getInt("attempt_count"),
                    "activeAttemptId", attempt, "returnPending", pending,
                    "canStart", actionAllowed && "SHIPPED".equals(status), "canDeliver", record, "canFail", record,
                    "canReturn", actionAllowed && Set.of("SHIPPED", "DELIVERY_FAILED").contains(status));
        }, workerId);
    }

    private List<Map<String, Object>> orderProducts(long orderId) {
        return database.query("""
                SELECT product.sku, product.name, item.quantity
                FROM public.order_item item JOIN public.product product USING (product_id)
                WHERE item.order_id = ? ORDER BY item.order_item_id
                """, (row, index) -> fields("sku", row.getString("sku"), "name", row.getString("name"),
                "quantity", row.getInt("quantity")), orderId);
    }

    private List<Map<String, Object>> attempts(long workerId, String workerName) {
        return database.query("""
                SELECT a.delivery_attempt_id, a.package_id, a.start_time, a.end_time, a.outcome,
                       a.failure_reason, a.internal_note, orders.order_reference
                FROM public.delivery_attempt a
                JOIN public.delivery_package p ON p.package_id = a.package_id
                JOIN public.customer_order orders ON orders.order_id = p.order_id
                WHERE a.delivery_worker_user_id = ?
                ORDER BY a.start_time DESC, a.delivery_attempt_id DESC
                LIMIT 300
                """, (row, index) -> fields(
                "id", row.getString("delivery_attempt_id"), "packageId", row.getString("package_id"),
                "orderReference", row.getString("order_reference"), "workerName", workerName,
                "startedAt", time(row, "start_time"), "endedAt", time(row, "end_time"),
                "outcome", row.getString("outcome"), "reason", row.getString("failure_reason"),
                "note", row.getString("internal_note"),
                "proofs", proofMetadata(row.getLong("delivery_attempt_id"), workerId)), workerId);
    }

    private List<Map<String, Object>> proofMetadata(long attemptId, long workerId) {
        // Proof references stay private until a storage endpoint can check access.
        return database.query("""
                SELECT proof_id, proof_type, proof.created_at,
                       (attempt.outcome='IN_PROGRESS' AND orders.order_status='OUT_FOR_DELIVERY'
                        AND p.current_handler_user_id=attempt.delivery_worker_user_id
                        AND p.custody_status='DELIVERY'
                        AND proof.proof_reference LIKE 'delivery/%'
                        AND NOT EXISTS (SELECT 1 FROM delivery_assignment_history h
                            WHERE h.package_id=p.package_id AND h.assigned_at>=attempt.start_time
                              AND h.new_worker_user_id<>attempt.delivery_worker_user_id)) AS active
                FROM public.delivery_proof proof
                JOIN public.delivery_attempt attempt USING (delivery_attempt_id)
                JOIN public.delivery_package p ON p.package_id=attempt.package_id
                JOIN public.customer_order orders ON orders.order_id=p.order_id
                WHERE attempt.delivery_attempt_id = ? AND attempt.delivery_worker_user_id = ?
                ORDER BY proof.created_at, proof_id
                """, (row, index) -> fields("id", row.getString("proof_id"), "type", row.getString("proof_type"),
                "createdAt", time(row, "created_at"), "url",
                full != null && row.getBoolean("active") ? "/api/delivery/proofs/" + row.getString("proof_id") : null),
                attemptId, workerId);
    }

    private List<Map<String, Object>> notifications(long workerId) {
        return database.query("""
                SELECT notification_id, type, message, created_at, is_read
                FROM public.notification WHERE recipient_user_id = ?
                ORDER BY created_at DESC, notification_id DESC LIMIT 100
                """, (row, index) -> fields("id", row.getString("notification_id"), "type", row.getString("type"),
                "message", row.getString("message"), "createdAt", time(row, "created_at"),
                "read", row.getBoolean("is_read")), workerId);
    }

    private Map<String, Object> stats(long workerId, List<Map<String, Object>> tasks, List<Map<String, Object>> lists) {
        OffsetDateTime today = LocalDate.now(HONG_KONG).atStartOfDay(HONG_KONG).toOffsetDateTime();
        int deliveredToday = Objects.requireNonNull(database.queryForObject("""
                SELECT COUNT(*) FROM public.delivery_attempt
                WHERE delivery_worker_user_id = ? AND outcome = 'DELIVERED' AND end_time >= ?
                """, Integer.class, workerId, today));
        int failedAttempts = Objects.requireNonNull(database.queryForObject("""
                SELECT COUNT(*) FROM public.delivery_attempt WHERE delivery_worker_user_id = ? AND outcome = 'FAILED'
                """, Integer.class, workerId));
        return fields("readyLists", lists.stream().filter(list -> "OPEN".equals(list.get("status"))).count(), "inTransit", tasks.stream().filter(t -> IN_TRANSIT.contains(t.get("status"))).count(),
                "deliveredToday", deliveredToday,
                "pendingReturns", tasks.stream().filter(t -> Boolean.TRUE.equals(t.get("returnPending"))).count(),
                "failedAttempts", failedAttempts);
    }

    private static String time(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant().toString();
    }

    private static Map<String, Object> fields(Object... entries) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            fields.put((String) entries[i], entries[i + 1]);
        }
        return fields;
    }
}
