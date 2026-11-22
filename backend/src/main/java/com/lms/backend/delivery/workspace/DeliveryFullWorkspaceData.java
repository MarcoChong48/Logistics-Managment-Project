package com.lms.backend.delivery.workspace;

import static com.lms.backend.delivery.support.DeliveryValues.fields;

import com.lms.backend.delivery.pickup.DeliveryPickupService;
import com.lms.backend.delivery.attempts.DeliveryRetentionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("delivery-full")
class DeliveryFullWorkspaceData {
    private final JdbcTemplate db;
    private final DeliveryPickupService pickup;
    private final DeliveryRetentionStatus retention;

    DeliveryFullWorkspaceData(JdbcTemplate db, DeliveryPickupService pickup, DeliveryRetentionStatus retention) {
        this.db = db;
        this.pickup = pickup;
        this.retention = retention;
    }

    void refreshAssignments() {
        pickup.refreshAssignments();
    }

    List<Map<String,Object>> returns(long workerId) {
        return db.query("""
            SELECT r.return_id,r.package_id,r.reason,r.status,r.reported_at,r.received_at,r.condition,
                   r.failed_attempt,o.order_reference,o.order_status,p.delivery_hold_reason,
                   i.status AS instruction_status,
                   (r.return_id = (SELECT MAX(other.return_id) FROM delivery_return_receipt other
                    WHERE other.package_id=r.package_id)) AS current_receipt
            FROM delivery_return_receipt r
            JOIN delivery_package p USING (package_id)
            JOIN customer_order o ON o.order_id=p.order_id
            LEFT JOIN LATERAL (SELECT status FROM client_instruction
                WHERE return_custody_event_id=r.receipt_event_id AND status IN ('PENDING','APPROVED')
                ORDER BY submitted_at DESC,instruction_id DESC LIMIT 1) i ON true
            WHERE r.reported_by_user_id=?
            ORDER BY r.reported_at DESC,r.return_id DESC LIMIT 200
            """, (row, index) -> {
            String followUp = followUp(row);
            return fields("id", row.getString("return_id"), "packageId", row.getString("package_id"),
                "orderReference", row.getString("order_reference"), "reason", row.getString("reason"),
                "status", row.getString("status"), "createdAt", time(row, "reported_at"),
                "receivedAt", time(row, "received_at"), "condition", row.getString("condition"),
                "awaitingInstruction", "AWAITING_CLIENT_INSTRUCTION".equals(followUp), "followUpStatus", followUp);
        }, workerId);
    }

    private String followUp(ResultSet row) throws SQLException {
        if (!row.getBoolean("current_receipt") || !"RECEIVED".equals(row.getString("status"))) return null;
        String state = row.getString("order_status");
        if ("CANCELLED".equals(state) || "READY_FOR_REDELIVERY".equals(state)) return state;
        if (row.getString("delivery_hold_reason") != null) return "ON_HOLD";
        if (!row.getBoolean("failed_attempt") || !"DELIVERY_FAILED".equals(state)) return null;
        String instruction = row.getString("instruction_status");
        if (instruction == null) return "AWAITING_CLIENT_INSTRUCTION";
        return "PENDING".equals(instruction) ? "AWAITING_ADMIN_APPROVAL" : null;
    }

    Map<String,Object> storageHealth() {
        var check = retention.lastCheck();
        return fields("privateLocalStorage", true, "cloudConnector", "Place holder",
            "lastCleanupAt", check == null ? null : check.finishedAt().toString(),
            "lastError", check != null && !check.successful() ? "The last retention check did not finish." : null);
    }

    List<Map<String,Object>> retained(long workerId) {
        return db.query("""
            SELECT r.package_id,r.outcome,r.event_at,o.order_reference
            FROM delivery_retained_attempt r JOIN delivery_package p USING (package_id)
            JOIN customer_order o ON o.order_id=p.order_id WHERE r.worker_id=?
            ORDER BY r.event_at DESC
            """, (row, index) -> fields("packageId", row.getString("package_id"),
                "orderReference", row.getString("order_reference"), "outcome", row.getString("outcome"),
                "eventAt", time(row, "event_at"), "detailExpired", true), workerId);
    }

    List<Map<String,Object>> reviews(long workerId, List<Map<String,Object>> lists, List<Map<String,Object>> returns) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (Map<String,Object> list : lists) {
            if (Boolean.TRUE.equals(list.get("stale"))) {
                result.add(fields("id", "STALE-" + list.get("id"), "type", "STALE_CLAIM",
                    "title", "Stale pickup confirmation", "reference", list.get("qrCode"), "createdAt", list.get("claimedAt"),
                    "message", "Warehouse or Admin must review the list before releasing uncollected packages."));
            }
        }
        result.addAll(db.query("""
            SELECT p.package_id,o.order_reference,failures.total,failures.last_failure
            FROM delivery_package p
            JOIN customer_order o USING (order_id)
            JOIN LATERAL (
                SELECT COUNT(*) AS total, MAX(ended_at) AS last_failure
                FROM (
                    SELECT end_time AS ended_at FROM delivery_attempt
                    WHERE package_id=p.package_id AND outcome='FAILED'
                    UNION ALL
                    SELECT event_at FROM delivery_retained_attempt
                    WHERE package_id=p.package_id AND outcome='FAILED'
                ) events
            ) failures ON failures.total >= 2
            WHERE o.order_status='DELIVERY_FAILED' AND (
                (p.custody_status='DELIVERY' AND p.current_handler_user_id=?)
                OR EXISTS (
                    SELECT 1 FROM delivery_return_receipt r WHERE r.package_id=p.package_id
                    AND r.return_id=(SELECT MAX(latest.return_id) FROM delivery_return_receipt latest
                        WHERE latest.package_id=p.package_id)
                    AND r.reported_by_user_id=?
                )
            )
            ORDER BY failures.last_failure DESC, p.package_id
            """, (row, index) -> fields(
                "id", "FAILURES-" + row.getString("package_id"), "type", "REPEATED_FAILURE",
                "title", "Repeated failures need review", "reference", row.getString("order_reference"),
                "createdAt", time(row, "last_failure"),
                "message", row.getLong("total") + " failed attempts need Admin review."),
            workerId, workerId));
        for (Map<String,Object> receipt : returns) {
            if (!"AWAITING_CLIENT_INSTRUCTION".equals(receipt.get("followUpStatus"))) continue;
            if (!(receipt.get("receivedAt") instanceof String receivedAt)) continue;
            Instant recorded = Instant.parse(receivedAt);
            long days = Duration.between(recorded, Instant.now()).toDays();
            if (days >= 3) {
                boolean finalReview = days >= 7;
                result.add(fields("id", "RESPONSE-" + receipt.get("id"), "type", finalReview ? "FINAL_REVIEW" : "CLIENT_REMINDER",
                    "title", finalReview ? "Final response review" : "Client response reminder",
                    "reference", receipt.get("orderReference"), "createdAt", receipt.get("receivedAt"),
                    "message", "Place holder: the responsible role reviews this return."));
            }
        }
        return result;
    }

    private static String time(ResultSet row, String name) throws SQLException {
        OffsetDateTime stamp = row.getObject(name, OffsetDateTime.class);
        return stamp == null ? null : stamp.toInstant().toString();
    }
}
