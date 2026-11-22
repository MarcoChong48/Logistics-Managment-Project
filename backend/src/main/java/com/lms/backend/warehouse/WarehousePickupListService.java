package com.lms.backend.warehouse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.delivery.pickup.DeliveryPickupService;
import com.lms.backend.delivery.support.DeliveryCommands;
import com.lms.backend.shared.database.FulfillmentEligibility;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUserReader;

@Service
@Profile("delivery-full")
class WarehousePickupListService {

    private final JdbcClient db;
    private final SharedCurrentUserReader users;
    private final DeliveryCommands commands;
    private final DeliveryPickupService pickup;
    private final FulfillmentEligibility eligibility;
    private final SharedAuditLogWriter audit;

    WarehousePickupListService(JdbcClient db, SharedCurrentUserReader users, DeliveryCommands commands,
            DeliveryPickupService pickup, FulfillmentEligibility eligibility, SharedAuditLogWriter audit) {
        this.db = db;
        this.users = users;
        this.commands = commands;
        this.pickup = pickup;
        this.eligibility = eligibility;
        this.audit = audit;
    }

    Map<String, Object> create(String orderReference, UUID requestId) {
        if (requestId == null) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "WAREHOUSE_REQUEST_ID_REQUIRED",
                    "Provide a request ID before creating a pickup list.");
        }
        String reference = orderReference.strip();
        long actor = users.sharedReadCurrentUser().userID();
        // DeliveryCommands takes its transaction advisory lock before any package or order locks.
        return commands.run(actor, "WAREHOUSE_CREATE_PICKUP_LIST", reference,
                Map.of("requestId", requestId.toString()), () -> createLocked(reference, actor));
    }

    private Map<String, Object> createLocked(String reference, long actor) {
        var packed = db.sql("""
                SELECT p.package_id, orders.order_id
                FROM delivery_package p JOIN customer_order orders USING (order_id)
                JOIN picking_task t ON t.order_id = orders.order_id
                WHERE orders.order_reference = :reference AND orders.order_status = 'READY_FOR_PICKUP'
                  AND t.task_status = 'PACKED' AND t.confirm_packing_time IS NOT NULL
                  AND p.custody_status = 'WAREHOUSE' AND p.current_handler_user_id IS NULL
                  AND p.delivery_hold_reason IS NULL
                  AND NOT EXISTS (SELECT 1 FROM delivery_return_receipt r WHERE r.package_id = p.package_id)
                  AND EXISTS (SELECT 1 FROM order_delivery_detail d WHERE d.order_id = orders.order_id)
                  AND NOT EXISTS (
                      SELECT 1 FROM pickup_list_item i JOIN pickup_list l USING (pickup_list_id)
                      WHERE i.package_id = p.package_id AND l.status <> 'CLOSED'
                  )
                FOR UPDATE OF p, orders
                """).param("reference", reference)
                .query((row, index) -> new Packed(row.getLong(1), row.getLong(2))).optional()
                .orElseThrow(() -> conflict("This order does not have a packed package ready for first pickup."));
        if (eligibility.heldPayment(packed.orderId()).isEmpty()) {
            throw conflict("Resolve the payment and held reservations before creating a pickup list.");
        }

        long listId = pickup.createList(actor, List.of(packed.packageId()), null);
        String listQr = db.sql("SELECT list_qr_code FROM pickup_list WHERE pickup_list_id = :id")
                .param("id", listId).query(String.class).single();
        audit.sharedWriteAuditLog(actor, "WAREHOUSE_CREATE_PICKUP_LIST", "PICKUP_LIST", Long.toString(listId),
                null, Map.of("packageID", packed.packageId()));
        return listView(listId, listQr);
    }

    @Transactional(readOnly = true)
    Map<String, Object> activeList(String orderReference) {
        return db.sql("""
                SELECT l.pickup_list_id, l.list_qr_code FROM customer_order o JOIN delivery_package p USING (order_id)
                JOIN pickup_list_item i USING (package_id) JOIN pickup_list l USING (pickup_list_id)
                WHERE o.order_reference = :reference AND l.status IN ('OPEN', 'CLAIMED')
                ORDER BY l.pickup_list_id DESC LIMIT 1
                """).param("reference", orderReference.strip())
                .query((row, index) -> listView(row.getLong(1), row.getString(2))).optional().orElseGet(Map::of);
    }

    @Transactional(readOnly = true)
    byte[] qr(String orderReference, long listId) {
        String savedQr = db.sql("""
                SELECT l.list_qr_code FROM customer_order o JOIN delivery_package p USING (order_id)
                JOIN pickup_list_item i USING (package_id) JOIN pickup_list l USING (pickup_list_id)
                WHERE o.order_reference = :reference AND l.pickup_list_id = :id
                """).param("reference", orderReference.strip()).param("id", listId).query(String.class).optional()
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "WAREHOUSE_LIST_NOT_FOUND",
                        "No pickup list matches this order."));
        return WarehouseOrderService.warehouseEncodeQRCode(savedQr);
    }

    private static Map<String, Object> listView(long listId, String listQr) {
        return Map.of("pickupListId", Long.toString(listId), "listQrCode", listQr);
    }

    private static SharedRuleException conflict(String message) {
        return new SharedRuleException(HttpStatus.CONFLICT, "WAREHOUSE_HANDOFF_NOT_READY", message);
    }

    private record Packed(long packageId, long orderId) {}
}
