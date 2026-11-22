package com.lms.backend.delivery.returns;

import static com.lms.backend.delivery.support.DeliveryValues.bad;
import static com.lms.backend.delivery.support.DeliveryValues.conflict;
import static com.lms.backend.delivery.support.DeliveryValues.denied;
import static com.lms.backend.delivery.support.DeliveryValues.fields;
import static com.lms.backend.delivery.support.DeliveryValues.required;

import com.lms.backend.delivery.pickup.DeliveryPickupService;
import com.lms.backend.delivery.returns.DeliveryReturnService.PackageState;
import com.lms.backend.delivery.returns.DeliveryReturnService.Receipt;
import com.lms.backend.delivery.support.DeliveryCommands;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("delivery-full & admintest")
@ConditionalOnProperty(name = "lms.delivery.demo-enabled", havingValue = "true")
class DeliveryReturnDemoService {
    private final JdbcTemplate db;
    private final DeliveryCommands commands;
    private final DeliveryReturnService returns;
    private final DeliveryPickupService pickup;
    private final SharedAuditLogWriter audit;

    DeliveryReturnDemoService(JdbcTemplate db, DeliveryCommands commands, DeliveryReturnService returns,
            DeliveryPickupService pickup, SharedAuditLogWriter audit) {
        this.db = db;
        this.commands = commands;
        this.returns = returns;
        this.pickup = pickup;
        this.audit = audit;
    }

    Map<String,Object> confirmReceipt(long actor, long packageId, Map<String,Object> body) {
        return commands.run(actor, "DEMO_CONFIRM_RETURN", Long.toString(packageId), body, () -> {
            DemoReturn handoff = demoReturn(actor, packageId);
            PackageState pkg = handoff.pkg();
            Receipt receipt = handoff.receipt();
            String condition = required(body, "condition", 30);
            if (!Set.of("INTACT", "DAMAGED").contains(condition)) throw bad("Choose Intact or Damaged.");
            if (!"AWAITING_WAREHOUSE".equals(receipt.status())) {
                throw conflict("Warehouse receipt has already been recorded.");
            }
            if (!"DELIVERY".equals(pkg.custody()) || !Long.valueOf(actor).equals(pkg.holder())
                    || returns.pickupFor(packageId) != receipt.pickupId()) {
                throw conflict("This package is not awaiting a physical return from Delivery.");
            }
            boolean intact = "INTACT".equals(condition);
            long warehouse = handoff.fixture().warehouse();
            long event = returns.custodyEvent(packageId, receipt.pickupId(), warehouse, "RETURN_RECEIVED", receipt.id());

            restoreStock(pkg, receipt.id(), event, warehouse, intact);
            db.update("""
                    UPDATE delivery_return_receipt
                    SET status='RECEIVED',received_at=NOW(),received_by_user_id=?,condition=?,receipt_event_id=?
                    WHERE return_id=?
                    """, warehouse, condition, event, receipt.id());
            db.update("""
                    UPDATE delivery_package
                    SET custody_status='WAREHOUSE',current_handler_user_id=NULL,delivery_hold_reason=?
                    WHERE package_id=?
                    """, intact ? null : "DAMAGED_RETURN", packageId);
            if (intact && !receipt.failed()) {
                // A returned parcel with no failed visit can resume its prior pickup stage.
                setOrderStatus(pkg, priorRetryApproved(pkg, receipt) ? "READY_FOR_REDELIVERY" : "READY_FOR_PICKUP");
                pickup.createList(warehouse, List.of(packageId), null);
            }
            auditSimulation(warehouse, actor, "DELIVERY_DEMO_RETURN_RECEIVED", packageId, receipt.id(), condition);
            String nextStep = intact
                    ? (receipt.failed() ? "Client instruction and Admin approval are still required."
                                        : "A new pickup list is ready.")
                    : "Damaged units are held and unavailable. Warehouse must resolve the hold.";
            return fields("message", "Place holder: Warehouse receipt simulated. " + nextStep);
        });
    }

    Map<String,Object> clientInstruction(long actor, long packageId, Map<String,Object> body) {
        return commands.run(actor, "DEMO_INSTRUCTION", Long.toString(packageId), body, () -> {
            DemoReturn handoff = demoReturn(actor, packageId);
            requireFailedReceipt(handoff);
            PackageState pkg = handoff.pkg();
            Receipt receipt = handoff.receipt();
            String instruction = required(body, "instruction", 30);
            if (!Set.of("REDELIVER", "CANCEL").contains(instruction)) throw bad("Choose Redeliver or Cancel.");
            Long detailId = pkg.detailId();
            if ("REDELIVER".equals(instruction)) requireOrderDetail(pkg, detailId);

            // A new instruction replaces only pending instructions for this receipt.
            db.update("""
                    UPDATE client_instruction SET status='SUPERSEDED'
                    WHERE order_id=? AND return_custody_event_id=? AND status='PENDING'
                      AND instruction_type IN ('REDELIVER','CANCEL')
                    """, pkg.orderId(), receipt.eventId());
            db.update("""
                    INSERT INTO client_instruction
                        (order_id,client_user_id,delivery_detail_id,return_custody_event_id,instruction_type,status)
                    VALUES (?,?,?,?,?,'PENDING')
                    """, pkg.orderId(), pkg.clientId(), "REDELIVER".equals(instruction) ? detailId : null,
                    receipt.eventId(), instruction);
            auditSimulation(pkg.clientId(), actor, "DELIVERY_DEMO_CLIENT_INSTRUCTION", packageId, receipt.id(), instruction);
            return fields("message", "Place holder: current Client instruction simulated. Admin approval is still required.");
        });
    }

    Map<String,Object> adminDecision(long actor, long packageId, Map<String,Object> body) {
        return commands.run(actor, "DEMO_DECISION", Long.toString(packageId), body, () -> {
            DemoReturn handoff = demoReturn(actor, packageId);
            requireFailedReceipt(handoff);
            PackageState pkg = handoff.pkg();
            Receipt receipt = handoff.receipt();
            var saved = db.query("""
                    SELECT instruction_id,instruction_type,delivery_detail_id
                    FROM client_instruction
                    WHERE order_id=? AND return_custody_event_id=? AND status='PENDING'
                      AND instruction_type IN ('REDELIVER','CANCEL')
                    ORDER BY submitted_at DESC,instruction_id DESC LIMIT 1 FOR UPDATE
                    """, (row, index) -> new Instruction(row.getLong("instruction_id"),
                    row.getString("instruction_type"), row.getObject("delivery_detail_id", Long.class)),
                    pkg.orderId(), receipt.eventId());
            if (saved.isEmpty()) throw conflict("A current Client instruction is required before Admin approval.");
            Instruction instruction = saved.getFirst();
            String decision = required(body, "decision", 40);
            return switch (decision) {
                case "APPROVE_REDELIVERY" -> approveRetry(handoff, instruction, actor);
                case "APPROVE_CANCELLATION" -> approveCancellation(handoff, instruction, actor);
                default -> throw bad("Choose a valid Admin decision.");
            };
        });
    }

    private Map<String,Object> approveRetry(DemoReturn handoff, Instruction instruction, long actor) {
        if (!"REDELIVER".equals(instruction.type())) {
            throw conflict("A pending cancellation blocks redelivery. Use the current Client instruction.");
        }
        PackageState pkg = handoff.pkg();
        Receipt receipt = handoff.receipt();
        if (pkg.hold() != null || !"INTACT".equals(receipt.condition())) {
            throw conflict("Warehouse must resolve the damaged-goods hold before redelivery.");
        }
        Long detailId = instruction.detailId() == null ? pkg.detailId() : instruction.detailId();
        requireOrderDetail(pkg, detailId);
        long admin = handoff.fixture().admin();
        approveInstruction(instruction.id(), admin);
        db.update("UPDATE delivery_package SET delivery_detail_id=? WHERE package_id=?", detailId, pkg.id());
        setOrderStatus(pkg, "READY_FOR_REDELIVERY");
        pickup.createList(handoff.fixture().warehouse(), List.of(pkg.id()), null);
        auditSimulation(admin, actor, "DELIVERY_DEMO_REDELIVERY_APPROVED", pkg.id(), receipt.id(), "READY_FOR_REDELIVERY");
        return fields("message", "Place holder: Admin redelivery approval simulated. Scan the new pickup list before starting delivery.");
    }

    private Map<String,Object> approveCancellation(DemoReturn handoff, Instruction instruction, long actor) {
        if (!"CANCEL".equals(instruction.type())) throw conflict("The current Client instruction does not request cancellation.");
        PackageState pkg = handoff.pkg();
        Receipt receipt = handoff.receipt();
        long admin = handoff.fixture().admin();
        releaseStock(pkg, receipt.eventId(), admin);
        approveInstruction(instruction.id(), admin);
        db.update("UPDATE delivery_package SET delivery_hold_reason='CANCELLED' WHERE package_id=?", pkg.id());
        setOrderStatus(pkg, "CANCELLED");
        recordDemoRefund(pkg.orderId());
        for (long recipient : Set.of(actor, pkg.clientId())) {
            db.update("""
                    INSERT INTO notification (recipient_user_id,order_id,type,message)
                    VALUES (?,?,'ORDER_CANCELLED',?)
                    """, recipient, pkg.orderId(), "Order cancelled. Place holder refund integration must resolve the refund.");
        }
        auditSimulation(admin, actor, "DELIVERY_DEMO_CANCELLATION_APPROVED", pkg.id(), receipt.id(), "CANCELLED");
        return fields("message", "Place holder: Admin cancellation simulated. Cancellation is final. The refund service is a Place holder.");
    }

    private DemoReturn demoReturn(long actor, long packageId) {
        PackageState pkg = returns.lockPackage(packageId);
        // A Delivery worker can only use the simulation on a marked fixture they returned.
        var roles = db.query("""
                SELECT demo.warehouse_user_id,demo.admin_user_id FROM delivery_demo_order demo
                JOIN app_user warehouse ON warehouse.user_id=demo.warehouse_user_id AND warehouse.role='WAREHOUSE_WORKER'
                JOIN app_user admin ON admin.user_id=demo.admin_user_id AND admin.role='ADMIN'
                JOIN app_user client ON client.user_id=? AND client.role='CLIENT'
                WHERE demo.order_id=?
                """, (row, index) -> new FixtureActors(row.getLong("warehouse_user_id"), row.getLong("admin_user_id")),
                pkg.clientId(), pkg.orderId());
        if (roles.isEmpty()) throw denied();
        Receipt receipt = returns.latestReceipt(packageId);
        if (receipt.reporter() != actor) throw denied();
        return new DemoReturn(pkg, receipt, roles.getFirst());
    }

    private void requireFailedReceipt(DemoReturn handoff) {
        Receipt receipt = handoff.receipt();
        PackageState pkg = handoff.pkg();
        if (!"RECEIVED".equals(receipt.status()) || !receipt.failed() || receipt.eventId() == null
                || !"WAREHOUSE".equals(pkg.custody()) || !"DELIVERY_FAILED".equals(pkg.orderStatus())) {
            throw conflict("A returned failed delivery awaiting Admin review is required.");
        }
    }

    private boolean priorRetryApproved(PackageState pkg, Receipt current) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM client_instruction i
                    WHERE i.order_id=? AND i.instruction_type='REDELIVER' AND i.status='APPROVED'
                      AND i.return_custody_event_id=(
                          SELECT receipt_event_id FROM delivery_return_receipt
                          WHERE package_id=? AND return_id<? AND status='RECEIVED' AND failed_attempt
                          ORDER BY return_id DESC LIMIT 1
                      )
                )
                """, Boolean.class, pkg.orderId(), pkg.id(), current.id()));
    }

    private void restoreStock(PackageState pkg, long returnId, long event, long warehouse, boolean intact) {
        Payment payment = intact ? acceptedPayment(pkg.orderId()) : null;
        if (intact && payment == null) throw conflict("An accepted payment is required to restore reserved stock.");
        var lines = db.query("""
                SELECT order_item_id,product_id,quantity FROM order_item WHERE order_id=?
                ORDER BY product_id FOR UPDATE
                """, (row, index) -> new OrderLine(row.getLong("order_item_id"), row.getLong("product_id"),
                row.getInt("quantity")), pkg.orderId());
        if (lines.isEmpty()) throw conflict("The return has no order items.");
        if (!heldStock(pkg.orderId()).isEmpty()) throw conflict("Resolve the existing stock reservation before receiving this return.");

        // Test receipts use full order quantities. Damaged units stay unavailable.
        for (OrderLine line : lines) {
            int reserved = intact ? line.quantity() : 0;
            int unavailable = intact ? 0 : line.quantity();
            db.update("""
                    INSERT INTO delivery_return_line (return_id,product_id,quantity,unavailable_quantity)
                    VALUES (?,?,?,?)
                    """, returnId, line.productId(), line.quantity(), unavailable);
            int changed = db.update("""
                    UPDATE inventory SET physical_stock=physical_stock+?,reserved_stock=reserved_stock+?,
                        delivery_unavailable_stock=delivery_unavailable_stock+?,updated_at=NOW()
                    WHERE product_id=?
                    """, line.quantity(), reserved, unavailable, line.productId());
            if (changed != 1) throw conflict("Warehouse must reconcile this product's stock before receipt.");
            if (intact) {
                // Keep the previous consumed reservation as history.
                db.update("""
                        INSERT INTO stock_reservation (order_item_id,payment_id,quantity,status)
                        VALUES (?,?,?,'HELD')
                        """, line.itemId(), payment.id(), reserved);
            }
            movement(line.productId(), pkg.id(), event, warehouse, "CONFIRMED_RETURN", line.quantity(), reserved);
        }
    }

    private void releaseStock(PackageState pkg, long event, long admin) {
        for (StockHold hold : heldStock(pkg.orderId())) {
            int changed = db.update("""
                    UPDATE inventory SET reserved_stock=reserved_stock-?,updated_at=NOW()
                    WHERE product_id=? AND reserved_stock>=?
                    """, hold.quantity(), hold.productId(), hold.quantity());
            if (changed != 1) throw conflict("Warehouse must reconcile the reserved stock before cancellation.");
            db.update("UPDATE stock_reservation SET status='RELEASED',released_at=NOW() WHERE reservation_id=?", hold.id());
            movement(hold.productId(), pkg.id(), event, admin, "CANCEL_RELEASE", 0, -hold.quantity());
        }
    }

    private List<StockHold> heldStock(long orderId) {
        return db.query("""
                SELECT reservation.reservation_id,item.product_id,reservation.quantity
                FROM stock_reservation reservation JOIN order_item item USING (order_item_id)
                WHERE item.order_id=? AND reservation.status='HELD'
                ORDER BY item.product_id,reservation.reservation_id FOR UPDATE OF reservation
                """, (row, index) -> new StockHold(row.getLong("reservation_id"), row.getLong("product_id"),
                row.getInt("quantity")), orderId);
    }

    private void movement(long productId, long packageId, long event, long actor, String type, int physical, int reserved) {
        db.update("""
                INSERT INTO stock_movement
                    (product_id,package_id,custody_event_id,actor_user_id,movement_type,quantity_delta,reserved_delta)
                VALUES (?,?,?,?,?,?,?)
                """, productId, packageId, event, actor, type, physical, reserved);
    }

    private Payment acceptedPayment(long orderId) {
        var rows = db.query("""
                SELECT payment_id,amount FROM payment
                WHERE order_id=? AND payment_status='ACCEPTED' AND accepted_at IS NOT NULL
                ORDER BY accepted_at DESC,payment_id DESC LIMIT 1 FOR UPDATE
                """, (row, index) -> new Payment(row.getLong("payment_id"), row.getBigDecimal("amount")), orderId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void recordDemoRefund(long orderId) {
        Payment payment = acceptedPayment(orderId);
        if (payment == null || payment.amount().signum() <= 0) return;
        // Save one local request for this fixture. A test cannot call a payment provider.
        db.update("""
                INSERT INTO refund (payment_id,amount,refund_status)
                SELECT ?,?,'REQUESTED' WHERE NOT EXISTS (SELECT 1 FROM refund WHERE payment_id=?)
                """, payment.id(), payment.amount(), payment.id());
    }

    private void requireOrderDetail(PackageState pkg, Long detailId) {
        if (detailId == null || !Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM order_delivery_detail WHERE delivery_detail_id=? AND order_id=?)
                """, Boolean.class, detailId, pkg.orderId()))) {
            throw conflict("A saved delivery address for this order is required.");
        }
    }

    private void approveInstruction(long instructionId, long admin) {
        db.update("""
                UPDATE client_instruction SET status='APPROVED',resolved_by_admin_id=?,resolved_at=NOW()
                WHERE instruction_id=?
                """, admin, instructionId);
    }

    private void setOrderStatus(PackageState pkg, String status) {
        db.update("UPDATE customer_order SET order_status=? WHERE order_id=?", status, pkg.orderId());
    }

    private void auditSimulation(long roleActor, long requestingWorker, String action, long packageId, long returnId,
            String result) {
        audit.sharedWriteAuditLog(roleActor, action, "DELIVERY_PACKAGE", Long.toString(packageId), null,
                Map.of("returnId", returnId, "result", result, "requestedByWorkerId", requestingWorker));
    }

    private record DemoReturn(PackageState pkg, Receipt receipt, FixtureActors fixture) {}
    private record FixtureActors(long warehouse, long admin) {}
    private record Instruction(long id, String type, Long detailId) {}
    private record OrderLine(long itemId, long productId, int quantity) {}
    private record StockHold(long id, long productId, int quantity) {}
    private record Payment(long id, BigDecimal amount) {}
}
