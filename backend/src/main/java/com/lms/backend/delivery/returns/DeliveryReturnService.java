package com.lms.backend.delivery.returns;

import static com.lms.backend.delivery.support.DeliveryValues.conflict;
import static com.lms.backend.delivery.support.DeliveryValues.denied;
import static com.lms.backend.delivery.support.DeliveryValues.fields;
import static com.lms.backend.delivery.support.DeliveryValues.missing;
import static com.lms.backend.delivery.support.DeliveryValues.required;

import com.lms.backend.delivery.support.DeliveryCommands;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("delivery-full")
class DeliveryReturnService {
    private static final Set<String> RETURNABLE = Set.of("SHIPPED", "DELIVERY_FAILED");
    private final JdbcTemplate db;
    private final DeliveryCommands commands;
    private final SharedAuditLogWriter audit;

    DeliveryReturnService(JdbcTemplate db, DeliveryCommands commands, SharedAuditLogWriter audit) {
        this.db = db;
        this.commands = commands;
        this.audit = audit;
    }

    public Map<String,Object> report(long actor, long packageId, Map<String,Object> body) {
        return commands.run(actor, "RETURN", Long.toString(packageId), body, () -> {
            PackageState pkg = lockPackage(packageId);
            if (!"DELIVERY".equals(pkg.custody()) || !Long.valueOf(actor).equals(pkg.holder())) throw denied();
            if (!RETURNABLE.contains(pkg.orderStatus())) {
                throw conflict("Finish the active delivery attempt before reporting a return.");
            }

            long pickupId = pickupFor(pkg.id());
            if (Boolean.TRUE.equals(db.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM delivery_return_receipt
                        WHERE package_id=? AND (pickup_event_id=? OR status='AWAITING_WAREHOUSE'))
                    """, Boolean.class, pkg.id(), pickupId))) {
                throw conflict("This pickup already has a return report.");
            }

            var attempts = db.queryForList("""
                    SELECT delivery_attempt_id FROM delivery_attempt
                    WHERE package_id=? AND pickup_custody_event_id=?
                    ORDER BY start_time DESC, delivery_attempt_id DESC LIMIT 1
                    """, Long.class, pkg.id(), pickupId);
            Long attemptId = attempts.isEmpty() ? null : attempts.getFirst();
            long returnId = db.queryForObject("""
                    INSERT INTO delivery_return_receipt
                        (package_id,pickup_event_id,delivery_attempt_id,reported_by_user_id,reason,status,failed_attempt)
                    VALUES (?,?,?,?,?,'AWAITING_WAREHOUSE',?) RETURNING return_id
                    """, Long.class, pkg.id(), pickupId, attemptId, actor, required(body, "reason", 600),
                    "DELIVERY_FAILED".equals(pkg.orderStatus()));

            custodyEvent(pkg.id(), pickupId, actor, "RETURN_REQUESTED", returnId);
            audit.sharedWriteAuditLog(actor, "DELIVERY_RETURN_REPORTED", "DELIVERY_PACKAGE", Long.toString(pkg.id()),
                    null, Map.of("returnId", returnId, "status", "AWAITING_WAREHOUSE"));
            return fields("message", "Return reported. Warehouse confirms actual receipt before any stock changes.",
                    "returnId", Long.toString(returnId));
        });
    }

    PackageState lockPackage(long packageId) {
        var rows = db.query("""
                SELECT p.package_id,p.order_id,p.current_handler_user_id,p.delivery_detail_id,
                       p.custody_status,p.delivery_hold_reason,o.client_user_id,o.order_status
                FROM delivery_package p JOIN customer_order o USING (order_id)
                WHERE p.package_id=? FOR UPDATE OF p,o
                """, (row, index) -> new PackageState(row.getLong("package_id"), row.getLong("order_id"),
                row.getLong("client_user_id"), row.getObject("current_handler_user_id", Long.class),
                row.getObject("delivery_detail_id", Long.class), row.getString("custody_status"),
                row.getString("delivery_hold_reason"), row.getString("order_status")), packageId);
        if (rows.isEmpty()) throw missing("Delivery package not found.");
        return rows.getFirst();
    }

    Receipt latestReceipt(long packageId) {
        var rows = db.query("""
                SELECT return_id,pickup_event_id,reported_by_user_id,failed_attempt,status,condition,receipt_event_id
                FROM delivery_return_receipt WHERE package_id=?
                ORDER BY return_id DESC LIMIT 1 FOR UPDATE
                """, (row, index) -> new Receipt(row.getLong("return_id"), row.getLong("pickup_event_id"),
                row.getLong("reported_by_user_id"), row.getBoolean("failed_attempt"), row.getString("status"),
                row.getString("condition"), row.getObject("receipt_event_id", Long.class)), packageId);
        if (rows.isEmpty()) throw conflict("A recorded Warehouse return is required.");
        return rows.getFirst();
    }

    long pickupFor(long packageId) {
        var pickups = db.queryForList("""
                SELECT custody_event_id FROM package_custody_event
                WHERE package_id=? AND event_type='PICKED_UP'
                ORDER BY event_time DESC,custody_event_id DESC LIMIT 1
                """, Long.class, packageId);
        if (pickups.isEmpty()) throw conflict("A recorded pickup is required.");
        return pickups.getFirst();
    }

    long custodyEvent(long packageId, long pickupId, long actor, String kind, long returnId) {
        // The return event points to the same list as the physical pickup.
        return db.queryForObject("""
                INSERT INTO package_custody_event (package_id,event_type,actor_user_id,pickup_list_id,details)
                SELECT package_id,?,?,pickup_list_id,jsonb_build_object('returnId',?::bigint)
                FROM package_custody_event WHERE custody_event_id=? AND package_id=?
                RETURNING custody_event_id
                """, Long.class, kind, actor, returnId, pickupId, packageId);
    }

    record PackageState(long id, long orderId, long clientId, Long holder, Long detailId,
                        String custody, String hold, String orderStatus) {}
    record Receipt(long id, long pickupId, long reporter, boolean failed, String status,
                   String condition, Long eventId) {}
}
