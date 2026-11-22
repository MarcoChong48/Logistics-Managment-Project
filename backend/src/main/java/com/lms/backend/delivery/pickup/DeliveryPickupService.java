package com.lms.backend.delivery.pickup;

import static com.lms.backend.delivery.support.DeliveryValues.bad;
import static com.lms.backend.delivery.support.DeliveryValues.conflict;
import static com.lms.backend.delivery.support.DeliveryValues.denied;
import static com.lms.backend.delivery.support.DeliveryValues.fields;
import static com.lms.backend.delivery.support.DeliveryValues.id;
import static com.lms.backend.delivery.support.DeliveryValues.missing;
import static com.lms.backend.delivery.support.DeliveryValues.optional;
import static com.lms.backend.delivery.support.DeliveryValues.required;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.delivery.support.DeliveryCommands;
import com.lms.backend.shared.database.FulfillmentEligibility;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
@Profile("delivery-full")
public class DeliveryPickupService {
    private static final Set<String> PROBLEM_REASONS = Set.of(
            "VEHICLE_FULL", "PACKAGING_DAMAGED", "GOODS_DAMAGED", "OTHER");

    private static final String LIST_COLUMNS = """
            SELECT pickup_list_id, status, claimed_by_delivery_user_id,
                   assigned_to_delivery_user_id, created_by_worker_id
            FROM public.pickup_list
            """;

    private static final String PACKAGE_COLUMNS = """
            SELECT p.package_id, p.order_id, p.custody_status, p.current_handler_user_id,
                   p.delivery_detail_id, p.delivery_hold_reason, orders.order_status, orders.client_user_id
            FROM public.delivery_package p
            JOIN public.customer_order orders USING (order_id)
            """;

    private final JdbcTemplate db;
    private final DeliveryCommands commands;
    private final SharedAuditLogWriter audit;

    private final FulfillmentEligibility eligibility;

    DeliveryPickupService(JdbcTemplate db, DeliveryCommands commands, SharedAuditLogWriter audit,
            FulfillmentEligibility eligibility) {
        this.eligibility = eligibility;
        this.db = db;
        this.commands = commands;
        this.audit = audit;
    }

    public Map<String, Object> claim(long workerId, Map<String, Object> body) {
        String qr = required(body, "qrCode", 160);
        return commands.run(workerId, "CLAIM", qr, body, () -> {
            assignOpenLists(null, null);
            var lists = db.query(LIST_COLUMNS + " WHERE list_qr_code = ? FOR UPDATE",
                    DeliveryPickupService::mapList, qr);
            if (lists.isEmpty()) throw missing("Pickup list not found.");
            PickupList list = lists.getFirst();
            Long owner = list.claimedBy() == null ? list.assignedTo() : list.claimedBy();
            if (owner == null || owner != workerId) throw denied();
            if (!list.open()) throw conflict("This pickup list is closed.");
            if (list.claimedBy() != null) return fields("message", "Your pickup list is ready to continue.");

            // In the shared schema, the claim fields confirm an open list.
            db.update("""
                    UPDATE public.pickup_list
                    SET claimed_by_delivery_user_id = ?, claimed_at = NOW(), delivery_last_activity_at = NOW()
                    WHERE pickup_list_id = ?
                    """, workerId, list.id());
            audit.sharedWriteAuditLog(workerId, "DELIVERY_CONFIRM_PICKUP_LIST", "PICKUP_LIST",
                    Long.toString(list.id()), null, null);
            return fields("message", "Pickup list confirmed. Scan each package to record actual collection.");
        });
    }

    public Map<String, Object> collect(long workerId, long listId, Map<String, Object> body) {
        String qr = required(body, "packageQrCode", 160);
        return commands.run(workerId, "COLLECT", Long.toString(listId), body, () -> {
            requireCollector(workerId, listId);
            var packages = db.queryForList("""
                    SELECT p.package_id FROM public.pickup_list_item item
                    JOIN public.delivery_package p USING (package_id)
                    WHERE item.pickup_list_id = ? AND p.package_qr_code = ?
                    """, Long.class, listId, qr);
            if (packages.isEmpty()) throw missing("Package QR code not found in this pickup list.");
            long packageId = packages.getFirst();
            PackageState selected = lockPackage(packageId);
            if (!"PENDING".equals(lockItem(listId, packageId))) {
                throw conflict("This package has already been handled on this list.");
            }
            if (!selected.ready() || awaitingReturn(selected.id())) {
                throw conflict("This package is not ready for pickup.");
            }
            long paymentId = eligibility.heldPayment(selected.orderId())
                    .orElseThrow(() -> conflict("Warehouse must reconcile the payment and held reservations before pickup."));
            Long detailId = pickupDetail(selected);
            if (detailId == null) throw conflict("The delivery address or redelivery approval is missing.");

            long eventId = db.queryForObject("""
                    INSERT INTO public.package_custody_event
                        (package_id, event_type, actor_user_id, pickup_list_id)
                    VALUES (?, 'PICKED_UP', ?, ?) RETURNING custody_event_id
                    """, Long.class, packageId, workerId, listId);
            consumeStock(selected.orderId(), paymentId, packageId, eventId, workerId);

            db.update("""
                    UPDATE public.pickup_list_item SET collection_outcome = 'COLLECTED', collected_at = NOW()
                    WHERE pickup_list_id = ? AND package_id = ?
                    """, listId, packageId);
            db.update("""
                    UPDATE public.delivery_package
                    SET custody_status = 'DELIVERY', current_handler_user_id = ?, delivery_detail_id = ?
                    WHERE package_id = ?
                    """, workerId, detailId, packageId);
            db.update("UPDATE public.customer_order SET order_status = 'SHIPPED' WHERE order_id = ?", selected.orderId());
            touch(listId);
            audit.sharedWriteAuditLog(workerId, "DELIVERY_COLLECT_PACKAGE", "DELIVERY_PACKAGE",
                    Long.toString(packageId), null, Map.of("status", SharedOrderStatus.SHIPPED.name()));
            db.update("""
                    INSERT INTO public.notification (recipient_user_id, order_id, type, message)
                    VALUES (?, ?, 'SHIPPED', 'Your package has been collected for delivery.')
                    """, selected.clientId(), selected.orderId());
            return fields("message", "Package collected and stock movement saved.");
        });
    }

    public Map<String, Object> cannotCollect(long workerId, long listId, Map<String, Object> body) {
        long packageId = id(required(body, "packageId", 25));
        String reason = required(body, "reason", 40);
        String note = optional(body, "note", 600);
        if (!PROBLEM_REASONS.contains(reason)) throw bad("Choose a collection problem reason.");
        if ("OTHER".equals(reason) && note.isBlank()) throw bad("Add a note for Other.");

        return commands.run(workerId, "CANNOT_COLLECT", Long.toString(listId), body, () -> {
            requireCollector(workerId, listId);
            if (!"PENDING".equals(lockItem(listId, packageId))) {
                throw conflict("This package has already been handled on this list.");
            }
            PackageState selected = lockPackage(packageId);
            if (!selected.atWarehouse()) throw conflict("A collected package needs a return handoff.");
            db.update("""
                    UPDATE public.pickup_list_item
                    SET collection_outcome = 'CANNOT_COLLECT', cannot_collect_reason = ?, delivery_internal_note = ?
                    WHERE pickup_list_id = ? AND package_id = ?
                    """, reason, note.isBlank() ? null : note, listId, packageId);
            if (!"VEHICLE_FULL".equals(reason)) {
                db.update("UPDATE public.delivery_package SET delivery_hold_reason = ? WHERE package_id = ?",
                        reason, packageId);
            }
            touch(listId);
            audit.sharedWriteAuditLog(workerId, "DELIVERY_REPORT_PICKUP_PROBLEM", "DELIVERY_PACKAGE",
                    Long.toString(packageId), null, Map.of("reason", reason));
            return fields("message", "Collection problem saved.");
        });
    }

    public Map<String, Object> close(long workerId, long listId, Map<String, Object> body) {
        return commands.run(workerId, "CLOSE", Long.toString(listId), body,
                () -> closeList(workerId, listId));
    }

    private Map<String, Object> closeList(long workerId, long listId) {
        PickupList list = requireCollector(workerId, listId);
        var uncollected = db.queryForList("""
                SELECT package_id FROM public.pickup_list_item
                WHERE pickup_list_id = ? AND collection_outcome IN ('PENDING', 'CANNOT_COLLECT')
                ORDER BY package_id FOR UPDATE
                """, Long.class, listId);
        var eligible = new ArrayList<Long>();
        for (long packageId : uncollected) {
            var selected = lockPackage(packageId);
            if (!selected.atWarehouse()) throw conflict("Warehouse must confirm the uncollected package first.");
            if (selected.ready() && !awaitingReturn(selected.id())
                    && acceptedPayment(selected.orderId()) != null && pickupDetail(selected) != null) {
                eligible.add(packageId);
            }
        }
        db.update("""
                UPDATE public.pickup_list_item SET collection_outcome = 'RELEASED'
                WHERE pickup_list_id = ? AND collection_outcome IN ('PENDING', 'CANNOT_COLLECT')
                """, listId);
        db.update("""
                UPDATE public.pickup_list SET status = 'CLOSED', delivery_last_activity_at = NOW()
                WHERE pickup_list_id = ?
                """, listId);

        Long replacement = createList(list.createdBy(), eligible, null);
        audit.sharedWriteAuditLog(workerId, "DELIVERY_CLOSE_PICKUP_LIST",
                "PICKUP_LIST", Long.toString(listId), null, null);
        return fields("message", replacement == null ? "Pickup list closed."
                        : "Pickup list closed. A replacement list is ready for the remaining packages.",
                "replacementListId", replacement == null ? null : Long.toString(replacement));
    }

    @Transactional
    public Long createList(long warehouseActor, List<Long> packageIds, Long skipWorker) {
        if (packageIds.isEmpty()) return null;
        commands.lock();
        if (!Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public.app_user WHERE user_id = ? AND role = 'WAREHOUSE_WORKER'
                )
                """, Boolean.class, warehouseActor))) {
            throw conflict("This list needs a Warehouse creator.");
        }

        var distinct = packageIds.stream().distinct().sorted().toList();
        if (distinct.size() != packageIds.size()) throw bad("A package can only appear once per pickup list.");
        for (long packageId : distinct) {
            if (!lockPackage(packageId).ready()) throw conflict("A package is not ready for a new pickup list.");
            if (Boolean.TRUE.equals(db.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM public.pickup_list_item item JOIN public.pickup_list list USING (pickup_list_id)
                        WHERE item.package_id = ? AND list.status <> 'CLOSED'
                          AND item.collection_outcome IN ('PENDING', 'CANNOT_COLLECT')
                    )
                    """, Boolean.class, packageId))) {
                throw conflict("A package is still on an active pickup list.");
            }
        }
        Long listId = db.queryForObject("""
                INSERT INTO public.pickup_list (list_qr_code, created_by_worker_id, status)
                VALUES (?, ?, 'OPEN') RETURNING pickup_list_id
                """, Long.class, "PL-" + UUID.randomUUID(), warehouseActor);
        for (long packageId : distinct) {
            db.update("INSERT INTO public.pickup_list_item (pickup_list_id, package_id) VALUES (?, ?)", listId, packageId);
        }
        assignOpenLists(listId, skipWorker);
        return listId;
    }

    @Transactional
    public void refreshAssignments() {
        commands.lock();
        assignOpenLists(null, null);
    }

    // Hold the Delivery transaction lock while ranking workers and saving the assignments.
    private void assignOpenLists(Long replacement, Long skipWorker) {
        db.update("""
                UPDATE public.pickup_list list SET assigned_to_delivery_user_id = NULL
                WHERE status = 'OPEN' AND claimed_by_delivery_user_id IS NULL
                  AND assigned_to_delivery_user_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM public.app_user worker WHERE worker.user_id = list.assigned_to_delivery_user_id
                        AND worker.role = 'DELIVERY_WORKER' AND worker.account_status = 'ACTIVE'
                  )
                """);
        var pending = db.queryForList("""
                SELECT pickup_list_id FROM public.pickup_list
                WHERE status = 'OPEN' AND claimed_by_delivery_user_id IS NULL AND assigned_to_delivery_user_id IS NULL
                ORDER BY created_at, pickup_list_id FOR UPDATE
                """, Long.class);
        if (pending.isEmpty()) return;

        Map<Long, Integer> workload = new HashMap<>();
        db.query("""
                SELECT worker.user_id, COUNT(list.pickup_list_id) AS list_count
                FROM public.app_user worker
                LEFT JOIN public.pickup_list list ON list.status <> 'CLOSED'
                    AND COALESCE(list.claimed_by_delivery_user_id, list.assigned_to_delivery_user_id) = worker.user_id
                WHERE worker.role = 'DELIVERY_WORKER' AND worker.account_status = 'ACTIVE'
                GROUP BY worker.user_id
                """, row -> {
            workload.put(row.getLong("user_id"), row.getInt("list_count"));
        });
        if (workload.isEmpty()) return;

        var ranking = Comparator.comparingInt((Long worker) -> workload.get(worker)).thenComparingLong(Long::longValue);
        for (Long listId : pending) {
            Long excluded = listId.equals(replacement) && workload.size() > 1 ? skipWorker : null;
            Long worker = workload.keySet().stream().filter(id -> !id.equals(excluded)).min(ranking).orElseThrow();
            db.update("UPDATE public.pickup_list SET assigned_to_delivery_user_id = ? WHERE pickup_list_id = ?",
                    worker, listId);
            workload.merge(worker, 1, Integer::sum);
        }
    }

    private void consumeStock(long orderId, long paymentId, long packageId, long eventId, long workerId) {
        var items = db.query("""
                SELECT order_item_id, product_id, quantity FROM public.order_item
                WHERE order_id = ? ORDER BY product_id FOR UPDATE
                """, (row, ignored) -> new OrderItem(row.getLong("order_item_id"),
                    row.getLong("product_id"), row.getInt("quantity")), orderId);
        if (items.isEmpty()) throw conflict("This order has no items to collect.");

        for (OrderItem item : items) {
            var held = db.query("""
                    SELECT reservation_id, quantity FROM public.stock_reservation
                    WHERE order_item_id = ? AND payment_id = ? AND status = 'HELD'
                      AND consumed_at IS NULL AND released_at IS NULL
                    ORDER BY reservation_id FOR UPDATE
                    """, (row, ignored) -> new Reservation(row.getLong("reservation_id"), row.getInt("quantity")),
                    item.id(), paymentId);
            if (held.stream().mapToLong(Reservation::quantity).sum() != item.quantity()) {
                throw conflict("Warehouse must reconcile the stock reservation before pickup.");
            }
            int changed = db.update("""
                    UPDATE public.inventory
                    SET physical_stock = physical_stock - ?, reserved_stock = reserved_stock - ?, updated_at = NOW()
                    WHERE product_id = ? AND reserved_stock >= ?
                      AND physical_stock - delivery_unavailable_stock >= ?
                    """, item.quantity(), item.quantity(), item.productId(), item.quantity(), item.quantity());
            if (changed != 1) throw conflict("Warehouse must reconcile the available stock before pickup.");

            for (Reservation reservation : held) {
                db.update("""
                        UPDATE public.stock_reservation SET status = 'CONSUMED', consumed_at = NOW()
                        WHERE reservation_id = ?
                        """, reservation.id());
            }
            db.update("""
                    INSERT INTO public.stock_movement
                        (product_id, package_id, custody_event_id, actor_user_id, movement_type,
                         quantity_delta, reserved_delta)
                    VALUES (?, ?, ?, ?, 'PICKUP', ?, ?)
                    """, item.productId(), packageId, eventId, workerId, -item.quantity(), -item.quantity());
        }
    }

    private boolean awaitingReturn(long packageId) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public.delivery_return_receipt WHERE package_id = ? AND status = 'AWAITING_WAREHOUSE'
                )
                """, Boolean.class, packageId));
    }

    private Long acceptedPayment(long orderId) {
        var result = db.queryForList("""
                SELECT payment_id FROM public.payment
                WHERE order_id = ? AND payment_status = 'ACCEPTED' AND accepted_at IS NOT NULL AND amount > 0
                ORDER BY accepted_at DESC, payment_id DESC LIMIT 1 FOR UPDATE
                """, Long.class, orderId);
        return result.isEmpty() ? null : result.getFirst();
    }

    private Long pickupDetail(PackageState selected) {
        if (SharedOrderStatus.READY_FOR_REDELIVERY.name().equals(selected.orderStatus())) {
            var details = db.queryForList("""
                    SELECT detail.delivery_detail_id
                    FROM public.client_instruction instruction
                    JOIN public.order_delivery_detail detail
                      ON detail.order_id = instruction.order_id
                     AND detail.delivery_detail_id = COALESCE(instruction.delivery_detail_id, ?)
                    WHERE instruction.order_id = ? AND instruction.status = 'APPROVED'
                      AND instruction.instruction_type = 'REDELIVER'
                      AND instruction.return_custody_event_id = COALESCE(
                          (SELECT receipt_event_id FROM public.delivery_return_receipt
                           WHERE package_id = ? AND status = 'RECEIVED' AND failed_attempt = true
                           ORDER BY return_id DESC LIMIT 1),
                          (SELECT custody_event_id FROM public.package_custody_event
                           WHERE package_id = ? AND event_type = 'RETURN_RECEIVED'
                           ORDER BY event_time DESC, custody_event_id DESC LIMIT 1)
                      )
                      AND NOT EXISTS (
                          SELECT 1 FROM public.client_instruction cancellation
                          WHERE cancellation.order_id = instruction.order_id
                            AND cancellation.instruction_type = 'CANCEL' AND cancellation.status = 'PENDING'
                      )
                    ORDER BY instruction.submitted_at DESC, instruction.instruction_id DESC LIMIT 1
                    """, Long.class, selected.detailId(), selected.orderId(), selected.id(), selected.id());
            return details.isEmpty() ? null : details.getFirst();
        }

        var details = db.queryForList("""
                SELECT delivery_detail_id FROM public.order_delivery_detail
                WHERE order_id = ? AND delivery_detail_id = COALESCE(?, delivery_detail_id)
                ORDER BY version_no DESC, delivery_detail_id DESC LIMIT 1
                """, Long.class, selected.orderId(), selected.detailId());
        return details.isEmpty() ? null : details.getFirst();
    }

    private PickupList requireCollector(long workerId, long listId) {
        var lists = db.query(LIST_COLUMNS + " WHERE pickup_list_id = ? FOR UPDATE",
                DeliveryPickupService::mapList, listId);
        if (lists.isEmpty()) throw missing("Pickup list not found.");
        PickupList list = lists.getFirst();
        if (list.claimedBy() == null || list.claimedBy() != workerId) throw denied();
        if (!list.open()) throw conflict("This pickup list is closed.");
        return list;
    }

    private String lockItem(long listId, long packageId) {
        var rows = db.queryForList("""
                SELECT collection_outcome FROM public.pickup_list_item
                WHERE pickup_list_id = ? AND package_id = ? FOR UPDATE
                """, String.class, listId, packageId);
        if (rows.isEmpty()) throw missing("This package does not belong to this pickup list.");
        return rows.getFirst();
    }

    private PackageState lockPackage(long packageId) {
        var rows = db.query(PACKAGE_COLUMNS + " WHERE p.package_id = ? FOR UPDATE OF p, orders",
                (row, ignored) -> new PackageState(row.getLong("package_id"), row.getLong("order_id"),
                        row.getString("custody_status"), row.getObject("current_handler_user_id", Long.class),
                        row.getObject("delivery_detail_id", Long.class), row.getString("delivery_hold_reason"),
                        row.getString("order_status"), row.getLong("client_user_id")), packageId);
        if (rows.isEmpty()) throw missing("Package not found.");
        return rows.getFirst();
    }

    private void touch(long listId) {
        db.update("UPDATE public.pickup_list SET delivery_last_activity_at = NOW() WHERE pickup_list_id = ?", listId);
    }

    private static PickupList mapList(ResultSet row, int index) throws SQLException {
        return new PickupList(row.getLong("pickup_list_id"), row.getString("status"),
                row.getObject("claimed_by_delivery_user_id", Long.class),
                row.getObject("assigned_to_delivery_user_id", Long.class), row.getLong("created_by_worker_id"));
    }

    private record PickupList(long id, String status, Long claimedBy, Long assignedTo, long createdBy) {
        boolean open() {
            return "OPEN".equals(status) || "CLAIMED".equals(status);
        }
    }

    private record PackageState(long id, long orderId, String custody, Long holder, Long detailId,
                                String hold, String orderStatus, long clientId) {
        boolean atWarehouse() {
            return "WAREHOUSE".equals(custody) && holder == null;
        }

        boolean ready() {
            return atWarehouse() && (hold == null || hold.isBlank())
                    && (SharedOrderStatus.READY_FOR_PICKUP.name().equals(orderStatus)
                            || SharedOrderStatus.READY_FOR_REDELIVERY.name().equals(orderStatus));
        }
    }

    private record OrderItem(long id, long productId, int quantity) {}
    private record Reservation(long id, int quantity) {}
}
