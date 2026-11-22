package com.lms.backend.delivery.demo;

import static com.lms.backend.delivery.support.DeliveryValues.bad;
import static com.lms.backend.delivery.support.DeliveryValues.denied;
import static com.lms.backend.delivery.support.DeliveryValues.fields;
import static com.lms.backend.delivery.support.DeliveryValues.id;
import static com.lms.backend.delivery.support.DeliveryValues.required;

import com.lms.backend.delivery.pickup.DeliveryPickupService;
import com.lms.backend.delivery.support.DeliveryCommands;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedUserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("delivery-full & admintest")
@ConditionalOnProperty(name="lms.delivery.demo-enabled", havingValue="true")
@RequestMapping("/api/admin/delivery-preview")
public class DeliveryFixtureController {
    private final SharedCurrentUserReader users;
    private final DeliveryCommands commands;
    private final DeliveryPickupService pickup;
    private final JdbcTemplate db;

    DeliveryFixtureController(SharedCurrentUserReader users, DeliveryCommands commands,
                              DeliveryPickupService pickup, JdbcTemplate db) {
        this.users = users;
        this.commands = commands;
        this.pickup = pickup;
        this.db = db;
    }

    // Place holder: Test data uses the same tables as a future Warehouse handoff.
    @PostMapping("/fixtures")
    public Map<String,Object> create(@RequestBody Map<String,Object> body) {
        var admin = users.sharedReadCurrentUser();
        if (admin.role() != SharedUserRole.ADMIN) throw denied();
        long warehouse = id(required(body, "warehouseUserId", 25));
        long client = id(required(body, "clientUserId", 25));
        return commands.run(admin.userID(), "CREATE_DEMO_FIXTURES", "", body, () -> {
            requireRole(warehouse, "WAREHOUSE_WORKER");
            requireRole(client, "CLIENT");
            Integer workers = db.queryForObject("""
                SELECT count(*) FROM app_user
                WHERE role='DELIVERY_WORKER' AND account_status='ACTIVE'
                """, Integer.class);
            if (workers == null || workers == 0) throw bad("Create an active Delivery Worker before adding demo lists.");
            String batch = "DLY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
            List<Map<String,Object>> fixtures = new ArrayList<>();
            for (int number = 1; number <= 3; number++) {
                fixtures.add(fixture(batch + "-" + number, admin.userID(), warehouse, client));
            }
            return fields("message", "Three test lists were created.", "lists", fixtures);
        });
    }

    private void requireRole(long actor, String role) {
        Integer enabled = db.queryForObject("""
            SELECT count(*) FROM app_user WHERE user_id=? AND role=? AND account_status='ACTIVE'
            """, Integer.class, actor, role);
        if (enabled == null || enabled == 0) throw bad("Select an active " + role + " account.");
    }

    private Map<String,Object> fixture(String reference, long admin, long warehouse, long client) {
        long productId = insert("""
            INSERT INTO product(sku,name,current_price,low_stock_threshold)
            VALUES (?, 'Demo travel mug', 25, 2) RETURNING product_id
            """, reference);
        db.update("INSERT INTO inventory(product_id,physical_stock,reserved_stock) VALUES (?,10,2)", productId);

        long order = insert("""
            INSERT INTO customer_order(order_reference,client_user_id,order_status,payment_deadline,total_amount)
            VALUES (?,?,'READY_FOR_PICKUP',now() + interval '1 hour',50) RETURNING order_id
            """, reference, client);
        db.update("INSERT INTO delivery_demo_order(order_id,warehouse_user_id,admin_user_id) VALUES (?,?,?)",
            order, warehouse, admin);
        long item = insert("""
            INSERT INTO order_item(order_id,product_id,quantity,unit_price,picked_quantity)
            VALUES (?,?,2,25,2) RETURNING order_item_id
            """, order, productId);
        long payment = insert("""
            INSERT INTO payment(order_id,amount,payment_status,accepted_at)
            VALUES (?,50,'ACCEPTED',now()) RETURNING payment_id
            """, order);
        db.update("INSERT INTO stock_reservation(order_item_id,payment_id,quantity,status) VALUES (?,?,2,'HELD')",
            item, payment);
        db.update("""
            INSERT INTO order_delivery_detail(order_id,version_no,recipient_name,phone,address_text)
            VALUES (?,1,'Demo recipient','+852 0000 0000','Fictional test address, Hong Kong')
            """, order);
        String packageQr = "PKG-" + reference;
        long packageId = insert("""
            INSERT INTO delivery_package(order_id,package_qr_code,custody_status)
            VALUES (?,?,'WAREHOUSE') RETURNING package_id
            """, order, packageQr);
        long listId = pickup.createList(warehouse, List.of(packageId), null);
        Map<String,Object> list = db.queryForMap("""
            SELECT list_qr_code,assigned_to_delivery_user_id FROM pickup_list WHERE pickup_list_id=?
            """, listId);
        Object assigned = list.get("assigned_to_delivery_user_id");
        return fields("orderReference", reference, "orderId", Long.toString(order), "listId", Long.toString(listId), "listQRCode", list.get("list_qr_code"),
            "packageId", Long.toString(packageId), "packageQRCode", packageQr,
            "assignedTo", assigned == null ? null : assigned.toString());
    }

    private long insert(String sql, Object... values) {
        return java.util.Objects.requireNonNull(db.queryForObject(sql, Long.class, values));
    }
}
